package com.example.flutter_sms

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.content.FileProvider
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.IOException


class FlutterSmsPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var mChannel: MethodChannel
  private var activity: Activity? = null
  private var context: Context? = null
  private val REQUEST_CODE_SEND_SMS = 205

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    setupCallbackChannels(flutterPluginBinding.binaryMessenger)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    teardown()
    context = null
  }

  private fun setupCallbackChannels(messenger: BinaryMessenger) {
    mChannel = MethodChannel(messenger, "flutter_sms")
    mChannel.setMethodCallHandler(this)
  }

  private fun teardown() {
    mChannel.setMethodCallHandler(null)
  }


  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
        "sendSMS" -> {
          if (!canSendSMS()) {
            result.error(
                    "device_not_capable",
                    "The current device is not capable of sending text messages.",
                    "A device may be unable to send messages if it does not support messaging or if it is not currently configured to send messages. This only applies to the ability to send text messages via iMessage, SMS, and MMS.")
            return
          }
          val message = call.argument<String?>("message") ?: ""
          val recipients = call.argument<String?>("recipients") ?: ""
          val sendDirect = call.argument<Boolean?>("sendDirect") ?: false
          val filePaths = call.argument<List<String>?>("filePaths") ?: emptyList()
          if (filePaths.isNotEmpty()) {
            sendMmsDialog(result, recipients, message, filePaths)
          } else {
            sendSMS(result, recipients, message!!, sendDirect)
          }
        }
        "canSendSMS" -> result.success(canSendSMS())
        "canSendAttachments" -> result.success(canSendAttachments())
        else -> result.notImplemented()
    }
  }

  @TargetApi(Build.VERSION_CODES.ECLAIR)
  private fun canSendSMS(): Boolean {
    return try {
      // Check if we can create an SMS intent
      val intent = Intent(Intent.ACTION_SENDTO)
      intent.data = Uri.parse("smsto:")
      val activityInfo = intent.resolveActivityInfo(activity!!.packageManager, intent.flags.toInt())
      
      // If we can resolve the SMS intent, we can send SMS
      if (activityInfo != null && activityInfo.exported) {
        return true
      }
      
      // Alternative check: try to get SmsManager
      try {
        val smsManager = SmsManager.getDefault()
        // If we can get SmsManager without exception, we can likely send SMS
        return true
      } catch (e: SecurityException) {
        Log.w("FlutterSms", "SecurityException when checking SmsManager: ${e.message}")
        return false
      } catch (e: Exception) {
        Log.w("FlutterSms", "Exception when checking SmsManager: ${e.message}")
        return false
      }
    } catch (e: Exception) {
      Log.e("FlutterSms", "Error checking SMS capability: ${e.message}")
      false
    }
  }

  /**
   * True when the user's default SMS app accepts an image hand-off, which is
   * what [sendMmsDialog] relies on. False with no default SMS app (tablets
   * without telephony) rather than guessing at the chooser.
   */
  private fun canSendAttachments(): Boolean {
    val ctx = context ?: return false
    return try {
      val smsPackage = Telephony.Sms.getDefaultSmsPackage(ctx) ?: return false
      val probe = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        setPackage(smsPackage)
      }
      probe.resolveActivity(ctx.packageManager) != null
    } catch (e: Exception) {
      Log.w("FlutterSms", "Error checking attachment capability: ${e.message}")
      false
    }
  }

  private fun sendSMS(result: Result, phones: String, message: String, sendDirect: Boolean) {
    if (sendDirect) {
      sendSMSDirect(result, phones, message);
    }
    else {
      sendSMSDialog(result, phones, message);
    }
  }

  // private fun sendSMSDirect(result: Result, phones: String, message: String) {
  //   try {
  //     // SmsManager is android.telephony
  //     val sentIntent = PendingIntent.getBroadcast(activity, 0, Intent("SMS_SENT_ACTION"), PendingIntent.FLAG_IMMUTABLE)
  //     val mSmsManager = SmsManager.getDefault()
  //     val numbers = phones.split(";")

  //     for (num in numbers) {
  //       Log.d("Flutter SMS", "msg.length() : " + message.toByteArray().size)
  //       if (message.toByteArray().size > 80) {
  //         val partMessage = mSmsManager.divideMessage(message)
  //         mSmsManager.sendMultipartTextMessage(num, null, partMessage, null, null)
  //       } else {
  //         mSmsManager.sendTextMessage(num, null, message, sentIntent, null)
  //       }
  //     }

  //     result.success("SMS Sent!")
  //   } catch (e: SecurityException) {
  //     result.error("permission_denied", "SMS permission denied: ${e.message}", null)
  //   } catch (e: Exception) {
  //     result.error("send_failed", "Failed to send SMS: ${e.message}", null)
  //   }
  // }

private fun sendSMSDirect(result: Result, phones: String, message: String) {
    try {
        // Create PendingIntent with FLAG_IMMUTABLE for Android 12+ compatibility
        val sentIntent = PendingIntent.getBroadcast(
            activity, 
            0, 
            Intent("SMS_SENT_ACTION"), 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val smsManager = SmsManager.getDefault()
        val numbers = phones.split(";")
        
        for (num in numbers) {
            val trimmedNum = num.trim()
            if (trimmedNum.isEmpty()) continue
            
            Log.d("Flutter SMS", "Message length (bytes): ${message.toByteArray().size}")
            
            // Use more accurate SMS length calculation (160 chars for GSM 7-bit)
            if (message.length > 160 || message.toByteArray().size > 160) {
                val partMessages = smsManager.divideMessage(message)
                
                // Create sent and delivery intents for multipart messages
                val sentIntents = arrayListOf<PendingIntent>()
                val deliveryIntents = arrayListOf<PendingIntent>()
                
                repeat(partMessages.size) { index ->
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            activity,
                            index,
                            Intent("SMS_SENT_ACTION"),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                }
                
                smsManager.sendMultipartTextMessage(
                    trimmedNum,
                    null,
                    partMessages,
                    sentIntents,
                    deliveryIntents.takeIf { it.isNotEmpty() }
                )
            } else {
                smsManager.sendTextMessage(
                    trimmedNum,
                    null,
                    message,
                    sentIntent,
                    null
                )
            }
        }
        
        result.success("SMS Sent!")
        
    } catch (e: SecurityException) {
        Log.e("Flutter SMS", "Security exception: ${e.message}")
        result.error("permission_denied", "SMS permission denied: ${e.message}", null)
    } catch (e: IllegalArgumentException) {
        Log.e("Flutter SMS", "Invalid argument: ${e.message}")
        result.error("invalid_argument", "Invalid SMS parameters: ${e.message}", null)
    } catch (e: Exception) {
        Log.e("Flutter SMS", "SMS send failed: ${e.message}")
        result.error("send_failed", "Failed to send SMS: ${e.message}", null)
    }
}

  private fun sendSMSDialog(result: Result, phones: String, message: String) {
    try {
      val intent = Intent(Intent.ACTION_SENDTO)
      intent.data = Uri.parse("smsto:$phones")
      intent.putExtra("sms_body", message)
      intent.putExtra(Intent.EXTRA_TEXT, message)
      activity?.startActivityForResult(intent, REQUEST_CODE_SEND_SMS)
      result.success("SMS Sent!")
    } catch (e: Exception) {
      result.error("intent_failed", "Failed to open SMS app: ${e.message}", null)
    }
  }

  /**
   * Opens the default SMS app with [phones], [message] and the files at
   * [filePaths] attached. `smsto:` intents cannot carry a file, so this is an
   * `ACTION_SEND` aimed at the default SMS package with the legacy `address`
   * and `sms_body` extras the messaging apps read. Each file is first copied
   * into the plugin's cache folder (see [copyAttachments]) so the caller may
   * delete its own copy as soon as this returns: the messaging app reads the
   * content URI after the activity has launched.
   */
  private fun sendMmsDialog(result: Result, phones: String, message: String, filePaths: List<String>) {
    val ctx = context
    val act = activity
    if (ctx == null || act == null) {
      result.error("no_activity", "The plugin is not attached to an activity.", null)
      return
    }
    // Copying touches the disk, and a video can be large.
    Thread {
      val uris = try {
        copyAttachments(ctx, filePaths)
      } catch (e: IOException) {
        act.runOnUiThread { result.error("file_not_found", e.message, null) }
        return@Thread
      }
      act.runOnUiThread { launchMms(ctx, act, result, phones, message, uris) }
    }.start()
  }

  private fun launchMms(ctx: Context, act: Activity, result: Result, phones: String, message: String, uris: ArrayList<Uri>) {
    val intent = if (uris.size == 1) {
      Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
      Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.type = mimeTypeFor(ctx, uris)
    intent.putExtra("address", phones)
    intent.putExtra("sms_body", message)
    intent.putExtra(Intent.EXTRA_TEXT, message)
    // Android 10+ share targets read the URI permission off the ClipData.
    val clip = ClipData.newUri(ctx.contentResolver, "attachment", uris[0])
    for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
    intent.clipData = clip
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // Aim at the default SMS app so the user is not shown a chooser.
    val smsPackage = try { Telephony.Sms.getDefaultSmsPackage(ctx) } catch (e: Exception) { null }
    if (smsPackage != null) {
      intent.setPackage(smsPackage)
      for (uri in uris) {
        ctx.grantUriPermission(smsPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }

    try {
      act.startActivity(intent)
      result.success("SMS Sent!")
    } catch (e: ActivityNotFoundException) {
      if (smsPackage == null) {
        result.error("intent_failed", "No app on this device can send this attachment.", null)
        return
      }
      // The default SMS app rejected the attachment type: offer every app that takes it.
      intent.setPackage(null)
      try {
        act.startActivity(Intent.createChooser(intent, null))
        result.success("SMS Sent!")
      } catch (e2: Exception) {
        result.error("intent_failed", "Failed to open SMS app: ${e2.message}", null)
      }
    } catch (e: Exception) {
      result.error("intent_failed", "Failed to open SMS app: ${e.message}", null)
    }
  }

  /** One type for all attachments, widened to a wildcard when they differ. */
  private fun mimeTypeFor(ctx: Context, uris: List<Uri>): String {
    var common: String? = null
    for (uri in uris) {
      val type = ctx.contentResolver.getType(uri) ?: return "*/*"
      common = when {
        common == null -> type
        common == type -> common
        else -> {
          val major = type.substringBefore('/')
          if (common.startsWith("$major/")) "$major/*" else "*/*"
        }
      }
    }
    return common ?: "*/*"
  }

  /**
   * Copies each attachment into `cache/flutter_sms/<send>/<name>` and returns
   * content URIs for the copies, so the FileProvider only ever exposes that
   * one folder whatever path the caller passed. Copies older than a day are
   * removed first; the messaging app has long finished reading them by then.
   */
  @Throws(IOException::class)
  private fun copyAttachments(ctx: Context, filePaths: List<String>): ArrayList<Uri> {
    val dir = File(ctx.cacheDir, ATTACHMENT_DIR)
    if (!dir.isDirectory && !dir.mkdirs()) {
      throw IOException("Cannot create $dir")
    }
    val cutoff = System.currentTimeMillis() - ATTACHMENT_MAX_AGE_MS
    dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }

    val authority = "${ctx.packageName}.$AUTHORITY_SUFFIX"
    val uris = ArrayList<Uri>()
    filePaths.forEachIndexed { index, path ->
      val source = File(path)
      if (!source.isFile) {
        throw IOException("Attachment not found: $path")
      }
      // A per-send subfolder keeps the original file name, which some
      // messaging apps show to the recipient, without clashing with earlier sends.
      val sendDir = File(dir, "${System.currentTimeMillis()}_$index")
      if (!sendDir.mkdirs()) {
        throw IOException("Cannot create $sendDir")
      }
      val copy = File(sendDir, source.name)
      source.copyTo(copy)
      uris.add(FileProvider.getUriForFile(ctx, authority, copy))
    }
    return uris
  }

  companion object {
    /** Cache subfolder served by [FlutterSmsFileProvider]; see res/xml/flutter_sms_file_paths.xml. */
    private const val ATTACHMENT_DIR = "flutter_sms"
    private const val AUTHORITY_SUFFIX = "flutter_sms.fileprovider"
    private const val ATTACHMENT_MAX_AGE_MS = 24L * 60 * 60 * 1000
  }
}

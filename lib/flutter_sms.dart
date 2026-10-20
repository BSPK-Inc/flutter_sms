import 'dart:async';

import 'src/flutter_sms_platform.dart';

/// Open SMS Dialog on iOS/Android/Web
///
/// [filePaths] (Android only) attaches the files to the message. The default
/// SMS app opens with the recipients, the message and the files filled in;
/// the user still sends it. Check [canSendAttachments] first. The files are
/// copied before this returns, so the caller may delete them afterwards.
Future<String> sendSMS({
  required String message,
  required List<String> recipients,
  bool sendDirect = false,
  List<String>? filePaths,
}) =>
    FlutterSmsPlatform.instance.sendSMS(
      message: message,
      recipients: recipients,
      sendDirect: sendDirect,
      filePaths: filePaths,
    );

/// Launch SMS Url Scheme on all platforms
Future<bool> launchSms({
  String? message,
  String? number,
}) =>
    FlutterSmsPlatform.instance.launchSms(number, message);

/// Launch SMS Url Scheme on all platforms
Future<bool> launchSmsMulti({
  required String message,
  required List<String> numbers,
}) =>
    FlutterSmsPlatform.instance.launchSmsMulti(numbers, message);

/// Check if you can send SMS on this platform
Future<bool> canSendSMS() => FlutterSmsPlatform.instance.canSendSMS();

/// Check if the default SMS app accepts attachments via [sendSMS].
///
/// Only Android has an implementation; every other platform returns false.
Future<bool> canSendAttachments() =>
    FlutterSmsPlatform.instance.canSendAttachments();

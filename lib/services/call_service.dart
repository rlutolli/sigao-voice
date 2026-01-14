import 'package:flutter_callkit_incoming/flutter_callkit_incoming.dart';
import 'package:flutter_callkit_incoming/entities/entities.dart';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

class CallService {
  static final CallService _instance = CallService._internal();
  factory CallService() => _instance;
  CallService._internal();

  // Show a "Fake" Incoming System Call (Simulating a Sigao Invite)
  Future<void> showIncomingCall(String callerName, String handle) async {
    final params = CallKitParams(
      id: const Uuid().v4(),
      nameCaller: callerName,
      appName: 'Sigao Voice',
      avatar: 'https://i.pravatar.cc/100', // Placeholder
      handle: handle,
      type: 0, // Audio Call
      duration: 30000,
      textAccept: 'Accept Secure Call',
      textDecline: 'Decline',
      missedCallNotification: const NotificationParams(
        showNotification: true,
        isShowCallback: true,
        subtitle: 'Missed Sigao Call',
        callbackText: 'Call back',
      ),
      extra: <String, dynamic>{'userId': handle},
      headers: <String, dynamic>{'platform': 'flutter'},
      android: const AndroidParams(
        isCustomNotification: true,
        isShowLogo: false,
        ringtonePath: 'system_ringtone_default',
        backgroundColor: '#0955fa', // Sigao Blue
        backgroundUrl: 'https://i.pravatar.cc/500', 
        actionColor: '#4CAF50',
      ),
    );
    
    await FlutterCallkitIncoming.showCallkitIncoming(params);
  }

  Future<void> endAllCalls() async {
    await FlutterCallkitIncoming.endAllCalls();
  }
}

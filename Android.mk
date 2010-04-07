root-dir:=$(call my-dir)

include $(root-dir)/speex/Android.mk

include $(root-dir)/gsm/Android.mk

include $(root-dir)/eXosip/Android.mk

include $(root-dir)/osip/Android.mk

include $(root-dir)/../../linphone-android/linphone/oRTP/build/android/Android.mk

include $(root-dir)/../../linphone-android/linphone/mediastreamer2/build/android/Android.mk

include $(root-dir)/../../linphone-android/msandroid/Android.mk

include $(root-dir)/../../linphone-android/linphone/build/android/Android.mk




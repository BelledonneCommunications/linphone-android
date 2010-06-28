root-dir:=$(APP_PROJECT_PATH)

include $(root-dir)/submodules/externals/build/speex/Android.mk

include $(root-dir)/submodules/externals/build/gsm/Android.mk

include $(root-dir)/submodules/externals/build/exosip/Android.mk

include $(root-dir)/submodules/externals/build/osip/Android.mk

include $(root-dir)/submodules/linphone/oRTP/build/android/Android.mk

include $(root-dir)/submodules/linphone/mediastreamer2/build/android/Android.mk

#include $(root-dir)/submodules/msilbc/Android.mk

include $(root-dir)/submodules/linphone/build/android/Android.mk




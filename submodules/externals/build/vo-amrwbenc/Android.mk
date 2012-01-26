

LOCAL_PATH:= $(call my-dir)/../../vo-amrwbenc

BUILD_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libvoamrwbenc

LOCAL_SRC_FILES += \
    wrapper.c \
    common/cmnMemory.c \
    amrwbenc/src/autocorr.c \
    amrwbenc/src/az_isp.c \
    amrwbenc/src/bits.c \
    amrwbenc/src/c2t64fx.c \
    amrwbenc/src/c4t64fx.c \
    amrwbenc/src/convolve.c \
    amrwbenc/src/cor_h_x.c \
    amrwbenc/src/decim54.c \
    amrwbenc/src/deemph.c \
    amrwbenc/src/dtx.c \
    amrwbenc/src/g_pitch.c \
    amrwbenc/src/gpclip.c \
    amrwbenc/src/homing.c \
    amrwbenc/src/hp400.c \
    amrwbenc/src/hp50.c \
    amrwbenc/src/hp6k.c \
    amrwbenc/src/hp_wsp.c \
    amrwbenc/src/int_lpc.c \
    amrwbenc/src/isp_az.c \
    amrwbenc/src/isp_isf.c \
    amrwbenc/src/lag_wind.c \
    amrwbenc/src/levinson.c \
    amrwbenc/src/log2.c \
    amrwbenc/src/lp_dec2.c \
    amrwbenc/src/math_op.c \
    amrwbenc/src/mem_align.c \
    amrwbenc/src/oper_32b.c \
    amrwbenc/src/p_med_ol.c \
    amrwbenc/src/pit_shrp.c \
    amrwbenc/src/pitch_f4.c \
    amrwbenc/src/pred_lt4.c \
    amrwbenc/src/preemph.c \
    amrwbenc/src/q_gain2.c \
    amrwbenc/src/q_pulse.c \
    amrwbenc/src/qisf_ns.c \
    amrwbenc/src/qpisf_2s.c \
    amrwbenc/src/random.c \
    amrwbenc/src/residu.c \
    amrwbenc/src/scale.c \
    amrwbenc/src/stream.c \
    amrwbenc/src/syn_filt.c \
    amrwbenc/src/updt_tar.c \
    amrwbenc/src/util.c \
    amrwbenc/src/voAMRWBEnc.c \
    amrwbenc/src/voicefac.c \
    amrwbenc/src/wb_vad.c \
    amrwbenc/src/weight_a.c

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_SRC_FILES += \
    amrwbenc/src/asm/ARMV7/convolve_neon.s.neon \
    amrwbenc/src/asm/ARMV7/cor_h_vec_neon.s.neon \
    amrwbenc/src/asm/ARMV7/Deemph_32_neon.s.neon \
    amrwbenc/src/asm/ARMV7/Dot_p_neon.s.neon \
    amrwbenc/src/asm/ARMV7/Filt_6k_7k_neon.s.neon \
    amrwbenc/src/asm/ARMV7/Norm_Corr_neon.s.neon \
    amrwbenc/src/asm/ARMV7/pred_lt4_1_neon.s.neon \
    amrwbenc/src/asm/ARMV7/residu_asm_neon.s.neon \
    amrwbenc/src/asm/ARMV7/scale_sig_neon.s.neon \
    amrwbenc/src/asm/ARMV7/Syn_filt_32_neon.s.neon \
    amrwbenc/src/asm/ARMV7/syn_filt_neon.s.neon

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/asm/ARMV5E
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/asm/ARMV7

LOCAL_CFLAGS += -DARM -DARMV7 -DASM_OPT

else
ifeq ($(TARGET_ARCH_ABI),armeabi-v5)
LOCAL_SRC_FILES += \
    amrwbenc/src/asm/ARMV5E/convolve_opt.s \
    amrwbenc/src/asm/ARMV5E/cor_h_vec_opt.s \
    amrwbenc/src/asm/ARMV5E/Deemph_32_opt.s \
    amrwbenc/src/asm/ARMV5E/Dot_p_opt.s \
    amrwbenc/src/asm/ARMV5E/Filt_6k_7k_opt.s \
    amrwbenc/src/asm/ARMV5E/Norm_Corr_opt.s \
    amrwbenc/src/asm/ARMV5E/pred_lt4_1_opt.s \
    amrwbenc/src/asm/ARMV5E/residu_asm_opt.s \
    amrwbenc/src/asm/ARMV5E/scale_sig_opt.s \
    amrwbenc/src/asm/ARMV5E/Syn_filt_32_opt.s \
    amrwbenc/src/asm/ARMV5E/syn_filt_opt.s

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/asm/ARMV5E

LOCAL_CFLAGS += += -DARM -DASM_OPT
endif
endif

LOCAL_ARM_MODE := arm

#for including config.h:
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/amrwbenc/inc/ \
	$(LOCAL_PATH)/common/include/

include $(BUILD_STATIC_LIBRARY)



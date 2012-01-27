

LOCAL_PATH:= $(call my-dir)/../../opencore-amr

BUILD_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libopencoreamr

_ADD_COMMON=0
ifneq ($(BUILD_AMRWB),0)
_ADD_COMMON=1
endif
ifneq ($(BUILD_AMRNB),0)
_ADD_COMMON=1
endif

ifeq ($(BUILD_AMRNB),light)
LOCAL_SRC_FILES += \
        amrnb/wrapper.cpp

LOCAL_SRC_FILES += \
	../build/opencore-amr/stubs.cpp

#for including config.h:
LOCAL_C_INCLUDES += \
        $(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/include \
        $(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/include \
        $(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src \
        $(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src \
        $(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/common/include \
        $(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/common/dec/include \
        $(LOCAL_PATH)/amrnb

#in this mode we try to dynamically link against the opencore-amr provided by android
LOCAL_CFLAGS += -include ../build/opencore-amr/stubs.h
endif

ifeq ($(BUILD_AMRNB),full)
#in the other mode (full) we build our own opencore-amr.

#common files
LOCAL_SRC_FILES += \
        amrnb/wrapper.cpp

LOCAL_SRC_FILES += \
	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/add.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/az_lsp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/bitno_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/bitreorder_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/c2_9pf_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/div_s.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/gains_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/gc_pred.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/get_const_tbls.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/gmed_n.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/grid_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/gray_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/int_lpc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/inv_sqrt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/inv_sqrt_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/l_shr_r.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/log2.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/log2_norm.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/log2_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/lsfwt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/lsp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/lsp_az.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/lsp_lsf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/lsp_lsf_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/lsp_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/mult_r.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/norm_l.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/norm_s.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/overflow_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/ph_disp_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/pow2.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/pow2_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/pred_lt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/q_plsf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/q_plsf_3.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/q_plsf_3_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/q_plsf_5.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/q_plsf_5_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/qua_gain_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/reorder.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/residu.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/round.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/shr.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/shr_r.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/sqrt_l.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/sqrt_l_tbl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/sub.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/syn_filt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/weight_a.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/common/src/window_tab.cpp

#encoder files

LOCAL_SRC_FILES += \
	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/amrencode.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/autocorr.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/c1035pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/c2_11pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/c2_9pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/c3_14pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/c4_17pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/c8_31pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/calc_cor.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/calc_en.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/cbsearch.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/cl_ltp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/cod_amr.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/convolve.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/cor_h.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/cor_h_x.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/cor_h_x2.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/corrwght_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/div_32.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/dtx_enc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/enc_lag3.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/enc_lag6.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/enc_output_format_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/ets_to_if2.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/ets_to_wmf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/g_adapt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/g_code.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/g_pitch.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/gain_q.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/hp_max.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/inter_36.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/inter_36_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/l_abs.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/l_comp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/l_extract.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/l_negate.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/lag_wind.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/lag_wind_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/levinson.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/lpc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/ol_ltp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/p_ol_wgh.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/pitch_fr.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/pitch_ol.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/pre_big.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/pre_proc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/prm2bits.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/q_gain_c.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/q_gain_p.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/qgain475.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/qgain795.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/qua_gain.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/s10_8pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/set_sign.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/sid_sync.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/sp_enc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/spreproc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/spstproc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/ton_stab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src/vad1.cpp

#decoder files
#	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/decoder_gsm_amr.cpp \


LOCAL_SRC_FILES += \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/a_refl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/agc.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/amrdecode.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/b_cn_cod.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/bgnscd.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/c_g_aver.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d1035pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d2_11pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d2_9pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d3_14pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d4_17pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d8_31pf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d_gain_c.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d_gain_p.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d_plsf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d_plsf_3.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/d_plsf_5.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/dec_amr.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/dec_gain.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/dec_input_format_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/dec_lag3.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/dec_lag6.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/dtx_dec.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/ec_gains.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/ex_ctrl.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/if2_to_ets.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/int_lsf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/lsp_avg.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/ph_disp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/post_pro.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/preemph.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/pstfilt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/qgain475_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/sp_dec.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src/wmf_to_ets.cpp

#for including config.h:
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/include \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/include \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/dec/src \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/enc/src \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_nb/common/include \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/common/dec/include \
	$(LOCAL_PATH)/amrnb
endif

ifneq ($(BUILD_AMRWB),0)
LOCAL_SRC_FILES += \
        amrwb/wrapper.cpp

LOCAL_SRC_FILES += \
	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/agc2_amr_wb.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/band_pass_6k_7k.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/dec_acelp_2p_in_64.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/dec_acelp_4p_in_64.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/dec_alg_codebook.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/dec_gain2_amr_wb.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/deemphasis_32.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/dtx_decoder_amr_wb.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/get_amr_wb_bits.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/highpass_400hz_at_12k8.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/highpass_50hz_at_12k8.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/homing_amr_wb_dec.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/interpolate_isp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/isf_extrapolation.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/isp_az.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/isp_isf.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/lagconceal.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/low_pass_filt_7k.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/median5.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/mime_io.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/noise_gen_amrwb.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/normalize_amr_wb.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/oversamp_12k8_to_16k.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/phase_dispersion.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/pit_shrp.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/pred_lt4.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/preemph_amrwb_dec.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/pvamrwb_math_op.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/pvamrwbdecoder.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/q_gain2_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/qisf_ns.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/qisf_ns_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/qpisf_2s.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/qpisf_2s_tab.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/scale_signal.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/synthesis_amr_wb.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/voice_factor.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/wb_syn_filt.cpp \
 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/weight_amrwb_lpc.cpp

#decoder files
# 	opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src/decoder_amr_wb.cpp \

#for including config.h:
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/include \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/amr_wb/dec/src \
	$(LOCAL_PATH)/opencore/codecs_v2/audio/gsm_amr/common/dec/include \
	$(LOCAL_PATH)/amrwb
endif


LOCAL_ARM_MODE := arm

#Common
ifeq ($(_ADD_COMMON),1)
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/oscl
endif

include $(BUILD_STATIC_LIBRARY)



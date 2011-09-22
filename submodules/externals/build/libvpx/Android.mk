
LOCAL_PATH:= $(call my-dir)/../../libvpx
include $(CLEAR_VARS)

LOCAL_MODULE := libvpx

LOCAL_ARM_MODE := arm
ASM := s

# vpx subfolder [vpx_codec.mk]
LOCAL_SRC_FILES = \
	vpx/src/vpx_decoder.c \
	vpx/src/vpx_decoder_compat.c \
	vpx/src/vpx_encoder.c \
	vpx/src/vpx_codec.c \
	vpx/src/vpx_image.c

# vp8 subfolder [vp8_common.mk]
LOCAL_SRC_FILES += \
	vp8/common/alloccommon.c \
	vp8/common/blockd.c \
	vp8/common/debugmodes.c \
	vp8/common/defaultcoefcounts.c \
	vp8/common/entropy.c \
	vp8/common/entropymode.c \
	vp8/common/entropymv.c \
	vp8/common/extend.c \
	vp8/common/filter.c \
	vp8/common/findnearmv.c \
	vp8/common/generic/systemdependent.c \
	vp8/common/idctllm.c \
	vp8/common/invtrans.c \
	vp8/common/loopfilter.c \
	vp8/common/loopfilter_filters.c \
	vp8/common/mbpitch.c \
	vp8/common/modecont.c \
	vp8/common/modecontext.c \
	vp8/common/quant_common.c \
	vp8/common/recon.c \
	vp8/common/reconinter.c \
	vp8/common/reconintra.c \
	vp8/common/reconintra4x4.c \
	vp8/common/setupintrarecon.c \
	vp8/common/swapyv12buffer.c \
	vp8/common/treecoder.c \
	vp8/common/asm_com_offsets.c \
	vp8/common/arm/arm_systemdependent.c \
	vp8/common/arm/bilinearfilter_arm.c \
	vp8/common/arm/filter_arm.c \
	vp8/common/arm/loopfilter_arm.c \
	vp8/common/arm/reconintra_arm.c \
	vp8/common/arm/neon/recon_neon.c.neon

ASM_FILES = \
	vp8/common/arm/armv6/bilinearfilter_v6.$(ASM) \
	vp8/common/arm/armv6/copymem8x4_v6.$(ASM) \
	vp8/common/arm/armv6/copymem8x8_v6.$(ASM) \
	vp8/common/arm/armv6/copymem16x16_v6.$(ASM) \
	vp8/common/arm/armv6/dc_only_idct_add_v6.$(ASM) \
	vp8/common/arm/armv6/iwalsh_v6.$(ASM) \
	vp8/common/arm/armv6/filter_v6.$(ASM) \
	vp8/common/arm/armv6/idct_v6.$(ASM) \
	vp8/common/arm/armv6/loopfilter_v6.$(ASM) \
	vp8/common/arm/armv6/recon_v6.$(ASM) \
	vp8/common/arm/armv6/simpleloopfilter_v6.$(ASM) \
	vp8/common/arm/armv6/sixtappredict8x4_v6.$(ASM) \
	vp8/common/arm/neon/bilinearpredict4x4_neon.$(ASM).neon \
	vp8/common/arm/neon/bilinearpredict8x4_neon.$(ASM).neon \
	vp8/common/arm/neon/bilinearpredict8x8_neon.$(ASM).neon \
	vp8/common/arm/neon/bilinearpredict16x16_neon.$(ASM).neon \
	vp8/common/arm/neon/copymem8x4_neon.$(ASM).neon \
	vp8/common/arm/neon/copymem8x8_neon.$(ASM).neon \
	vp8/common/arm/neon/copymem16x16_neon.$(ASM).neon \
	vp8/common/arm/neon/dc_only_idct_add_neon.$(ASM).neon \
	vp8/common/arm/neon/iwalsh_neon.$(ASM).neon \
	vp8/common/arm/neon/loopfilter_neon.$(ASM).neon \
	vp8/common/arm/neon/loopfiltersimplehorizontaledge_neon.$(ASM).neon \
	vp8/common/arm/neon/loopfiltersimpleverticaledge_neon.$(ASM).neon \
	vp8/common/arm/neon/mbloopfilter_neon.$(ASM).neon \
	vp8/common/arm/neon/recon2b_neon.$(ASM).neon \
	vp8/common/arm/neon/recon4b_neon.$(ASM).neon \
	vp8/common/arm/neon/reconb_neon.$(ASM).neon \
	vp8/common/arm/neon/shortidct4x4llm_1_neon.$(ASM).neon \
	vp8/common/arm/neon/shortidct4x4llm_neon.$(ASM).neon \
	vp8/common/arm/neon/sixtappredict4x4_neon.$(ASM).neon \
	vp8/common/arm/neon/sixtappredict8x4_neon.$(ASM).neon \
	vp8/common/arm/neon/sixtappredict8x8_neon.$(ASM).neon \
	vp8/common/arm/neon/sixtappredict16x16_neon.$(ASM).neon \
	vp8/common/arm/neon/recon16x16mb_neon.$(ASM).neon \
	vp8/common/arm/neon/buildintrapredictorsmby_neon.$(ASM).neon \
	vp8/common/arm/neon/save_neon_reg.$(ASM).neon \

# vp8 subfolder [vp8cx.mk]
LOCAL_SRC_FILES += \
	vp8/vp8_cx_iface.c \
	vp8/encoder/asm_enc_offsets.c \
	vp8/encoder/bitstream.c \
	vp8/encoder/dct.c \
	vp8/encoder/encodeframe.c \
	vp8/encoder/encodeintra.c \
	vp8/encoder/encodemb.c \
	vp8/encoder/encodemv.c \
	vp8/encoder/ethreading.c \
	vp8/encoder/generic/csystemdependent.c \
	vp8/encoder/lookahead.c \
	vp8/encoder/mcomp.c \
	vp8/encoder/modecosts.c \
	vp8/encoder/onyx_if.c \
	vp8/encoder/pickinter.c \
	vp8/encoder/picklpf.c \
	vp8/encoder/psnr.c \
	vp8/encoder/quantize.c \
	vp8/encoder/ratectrl.c \
	vp8/encoder/rdopt.c \
	vp8/encoder/sad_c.c \
	vp8/encoder/segmentation.c \
	vp8/encoder/tokenize.c \
	vp8/encoder/treewriter.c \
	vp8/encoder/variance_c.c

# vp8 subfolder [vp8cx_arm.mk]
LOCAL_SRC_FILES += \
	vp8/encoder/arm/arm_csystemdependent.c \
	vp8/encoder/arm/quantize_arm.c \
	vp8/encoder/arm/picklpf_arm.c \
	vp8/encoder/arm/dct_arm.c \
	vp8/encoder/arm/variance_arm.c \
	vp8/encoder/arm/boolhuff_arm.c

ASM_FILES += \
	vp8/encoder/arm/armv5te/boolhuff_armv5te.$(ASM) \
	vp8/encoder/arm/armv5te/vp8_packtokens_armv5.$(ASM) \
	vp8/encoder/arm/armv5te/vp8_packtokens_mbrow_armv5.$(ASM) \
	vp8/encoder/arm/armv5te/vp8_packtokens_partitions_armv5.$(ASM) \
	vp8/encoder/arm/armv6/vp8_subtract_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_fast_fdct4x4_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_fast_quantize_b_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_sad16x16_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_variance16x16_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_variance_halfpixvar16x16_h_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_variance_halfpixvar16x16_v_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_variance_halfpixvar16x16_hv_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_mse16x16_armv6.$(ASM) \
	vp8/encoder/arm/armv6/vp8_variance8x8_armv6.$(ASM) \
	vp8/encoder/arm/armv6/walsh_v6.$(ASM) \
	vp8/encoder/arm/neon/fastfdct4x4_neon.$(ASM).neon \
	vp8/encoder/arm/neon/fastfdct8x4_neon.$(ASM).neon \
	vp8/encoder/arm/neon/fastquantizeb_neon.$(ASM).neon \
	vp8/encoder/arm/neon/sad8_neon.$(ASM).neon \
	vp8/encoder/arm/neon/sad16_neon.$(ASM).neon \
	vp8/encoder/arm/neon/shortfdct_neon.$(ASM).neon \
	vp8/encoder/arm/neon/subtract_neon.$(ASM).neon \
	vp8/encoder/arm/neon/variance_neon.$(ASM).neon \
	vp8/encoder/arm/neon/vp8_mse16x16_neon.$(ASM).neon \
	vp8/encoder/arm/neon/vp8_subpixelvariance8x8_neon.$(ASM).neon \
	vp8/encoder/arm/neon/vp8_subpixelvariance16x16_neon.$(ASM).neon \
	vp8/encoder/arm/neon/vp8_subpixelvariance16x16s_neon.$(ASM).neon \
	vp8/encoder/arm/neon/vp8_memcpy_neon.$(ASM).neon \
	vp8/encoder/arm/neon/vp8_shortwalsh4x4_neon.$(ASM).neon \

# vp8 subfolder [vp8dx.mk]
LOCAL_SRC_FILES += \
	vp8/vp8_dx_iface.c \
	vp8/decoder/dboolhuff.c \
	vp8/decoder/decodemv.c \
	vp8/decoder/decodframe.c \
	vp8/decoder/dequantize.c \
	vp8/decoder/detokenize.c \
	vp8/decoder/error_concealment.c \
	vp8/decoder/generic/dsystemdependent.c \
	vp8/decoder/onyxd_if.c \
	vp8/decoder/idct_blk.c \
	vp8/decoder/threading.c \
	vp8/decoder/reconintra_mt.c

# vp8 subfolder [vp8dx_arm.mk]
LOCAL_SRC_FILES += \
	vp8/decoder/arm/arm_dsystemdependent.c \
	vp8/decoder/asm_dec_offsets.c \
	vp8/decoder/arm/dequantize_arm.c \
	vp8/decoder/arm/neon/idct_blk_neon.c.neon \
	vp8/decoder/arm/armv6/idct_blk_v6.c

ASM_FILES += \
	vp8/decoder/arm/neon/idct_dequant_dc_full_2x_neon.$(ASM).neon \
	vp8/decoder/arm/neon/idct_dequant_dc_0_2x_neon.$(ASM).neon \
	vp8/decoder/arm/neon/dequant_idct_neon.$(ASM).neon \
	vp8/decoder/arm/neon/idct_dequant_full_2x_neon.$(ASM).neon \
	vp8/decoder/arm/neon/idct_dequant_0_2x_neon.$(ASM).neon \
	vp8/decoder/arm/neon/dequantizeb_neon.$(ASM).neon \
	vp8/decoder/arm/armv6/dequant_dc_idct_v6.$(ASM) \
	vp8/decoder/arm/armv6/dequant_idct_v6.$(ASM) \
	vp8/decoder/arm/armv6/dequantize_v6.$(ASM)

# vpx_mem subfolder [vpx_mem.mk]
LOCAL_SRC_FILES += vpx_mem/vpx_mem.c

# vpx_scale subfolder [vpx_scale.mk]
LOCAL_SRC_FILES += \
	vpx_scale/generic/vpxscale.c \
	vpx_scale/generic/yv12config.c \
	vpx_scale/generic/yv12extend.c \
	vpx_scale/arm/scalesystemdependent.c \
	vpx_scale/arm/yv12extend_arm.c \
	vpx_scale/generic/scalesystemdependent.c
ASM_FILES += \
	vpx_scale/arm/neon/vp8_vpxyv12_copyframe_func_neon.$(ASM).neon \
	vpx_scale/arm/neon/vp8_vpxyv12_copyframeyonly_neon.$(ASM).neon \
	vpx_scale/arm/neon/vp8_vpxyv12_copysrcframe_func_neon.$(ASM).neon \
	vpx_scale/arm/neon/vp8_vpxyv12_extendframeborders_neon.$(ASM).neon \

LOCAL_SRC_FILES += vpx_ports/arm_cpudetect.c

LOCAL_SRC_FILES += $(ASM_FILES)

LOCAL_MODULE_CLASS := STATIC_LIBRARIES

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH) \
   $(LOCAL_PATH)/vpx_ports \
	$(LOCAL_PATH)/vp8/common \
	$(LOCAL_PATH)/vp8/encoder \
	$(LOCAL_PATH)/vp8/decoder \
	$(LOCAL_PATH)/vp8 \
	$(LOCAL_PATH)/vpx_codec

include $(BUILD_STATIC_LIBRARY)

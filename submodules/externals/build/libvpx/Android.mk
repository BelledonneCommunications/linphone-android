
LOCAL_PATH:= $(call my-dir)/../../libvpx
include $(CLEAR_VARS)

LOCAL_MODULE := libvpx

LOCAL_CFLAGS := -O3 -fPIC -D_FORTIFY_SOURCE=0 -D_LARGEFILE_SOURCE -D_FILE_OFFSET_BITS=64 -Wall -Wdeclaration-after-statement -Wdisabled-optimization -Wpointer-arith -Wtype-limits -Wcast-qual -Wno-unused-function

LOCAL_ARM_MODE := arm
ASM := .asm.s


### vpx_mem.mk
MEM_SRCS = vpx_mem/vpx_mem.c

### vpx_scale
SCALE_SRCS = vpx_scale/generic/vpxscale.c
SCALE_SRCS += vpx_scale/generic/yv12config.c
SCALE_SRCS += vpx_scale/generic/yv12extend.c
SCALE_SRCS += vpx_scale/generic/gen_scalers.c

#arm
SCALE_SRCS += vpx_scale/arm/scalesystemdependent.c

#neon
SCALE_SRCS += vpx_scale/arm/neon/vp8_vpxyv12_copy_y_neon$(ASM).neon
SCALE_SRCS += vpx_scale/arm/neon/vp8_vpxyv12_copyframe_func_neon$(ASM).neon
SCALE_SRCS += vpx_scale/arm/neon/vp8_vpxyv12_copysrcframe_func_neon$(ASM).neon
SCALE_SRCS += vpx_scale/arm/neon/vp8_vpxyv12_extendframeborders_neon$(ASM).neon
SCALE_SRCS += vpx_scale/arm/neon/yv12extend_arm.c

### vp8cx_arm
#File list for arm
# encoder
VP8_CX_SRCS = vp8/encoder/arm/arm_csystemdependent.c

VP8_CX_SRCS += vp8/encoder/arm/dct_arm.c
VP8_CX_SRCS += vp8/encoder/arm/quantize_arm.c
VP8_CX_SRCS += vp8/encoder/arm/variance_arm.c

#File list for armv5te
# encoder
VP8_CX_SRCS += vp8/encoder/arm/boolhuff_arm.c
VP8_CX_SRCS += vp8/encoder/arm/armv5te/boolhuff_armv5te$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv5te/vp8_packtokens_armv5$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv5te/vp8_packtokens_mbrow_armv5$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv5te/vp8_packtokens_partitions_armv5$(ASM)

#File list for armv6
# encoder
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_subtract_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_short_fdct4x4_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_fast_quantize_b_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_sad16x16_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_variance16x16_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_variance_halfpixvar16x16_h_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_variance_halfpixvar16x16_v_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_variance_halfpixvar16x16_hv_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_mse16x16_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/vp8_variance8x8_armv6$(ASM)
VP8_CX_SRCS += vp8/encoder/arm/armv6/walsh_v6$(ASM)

#File list for neon
# encoder
VP8_CX_SRCS += vp8/encoder/arm/neon/fastquantizeb_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/picklpf_arm.c.neon
VP8_CX_SRCS += vp8/encoder/arm/neon/sad8_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/sad16_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/shortfdct_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/subtract_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/variance_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/vp8_mse16x16_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/vp8_subpixelvariance8x8_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/vp8_subpixelvariance16x16_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/vp8_subpixelvariance16x16s_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/vp8_memcpy_neon$(ASM).neon
VP8_CX_SRCS += vp8/encoder/arm/neon/vp8_shortwalsh4x4_neon$(ASM).neon

### vp8_cx
VP8_CX_SRCS += vp8/vp8_cx_iface.c

VP8_CX_SRCS += vp8/encoder/asm_enc_offsets.c
VP8_CX_SRCS += vp8/encoder/bitstream.c
VP8_CX_SRCS += vp8/encoder/dct.c
VP8_CX_SRCS += vp8/encoder/encodeframe.c
VP8_CX_SRCS += vp8/encoder/encodeintra.c
VP8_CX_SRCS += vp8/encoder/encodemb.c
VP8_CX_SRCS += vp8/encoder/encodemv.c
VP8_CX_SRCS += vp8/encoder/ethreading.c
VP8_CX_SRCS += vp8/encoder/generic/csystemdependent.c
VP8_CX_SRCS += vp8/encoder/lookahead.c
VP8_CX_SRCS += vp8/encoder/mcomp.c
VP8_CX_SRCS += vp8/encoder/modecosts.c
VP8_CX_SRCS += vp8/encoder/onyx_if.c
VP8_CX_SRCS += vp8/encoder/pickinter.c
VP8_CX_SRCS += vp8/encoder/picklpf.c
VP8_CX_SRCS += vp8/encoder/psnr.c
VP8_CX_SRCS += vp8/encoder/quantize.c
VP8_CX_SRCS += vp8/encoder/ratectrl.c
VP8_CX_SRCS += vp8/encoder/rdopt.c
VP8_CX_SRCS += vp8/encoder/sad_c.c
VP8_CX_SRCS += vp8/encoder/segmentation.c
VP8_CX_SRCS += vp8/encoder/tokenize.c
VP8_CX_SRCS += vp8/encoder/treewriter.c
VP8_CX_SRCS += vp8/encoder/variance_c.c

### vp8_common
VP8_COMMON_SRCS = vp8/common/alloccommon.c
VP8_COMMON_SRCS += vp8/common/asm_com_offsets.c
VP8_COMMON_SRCS += vp8/common/blockd.c
VP8_COMMON_SRCS += vp8/common/debugmodes.c
VP8_COMMON_SRCS += vp8/common/entropy.c
VP8_COMMON_SRCS += vp8/common/entropymode.c
VP8_COMMON_SRCS += vp8/common/entropymv.c
VP8_COMMON_SRCS += vp8/common/extend.c
VP8_COMMON_SRCS += vp8/common/filter.c
VP8_COMMON_SRCS += vp8/common/findnearmv.c
VP8_COMMON_SRCS += vp8/common/generic/systemdependent.c
VP8_COMMON_SRCS += vp8/common/idctllm.c
#VP8_COMMON_SRCS += vp8/common/invtrans.c
VP8_COMMON_SRCS += vp8/common/loopfilter.c
VP8_COMMON_SRCS += vp8/common/loopfilter_filters.c
VP8_COMMON_SRCS += vp8/common/mbpitch.c
VP8_COMMON_SRCS += vp8/common/modecont.c
VP8_COMMON_SRCS += vp8/common/modecontext.c
VP8_COMMON_SRCS += vp8/common/quant_common.c
#VP8_COMMON_SRCS += vp8/common/recon.c
VP8_COMMON_SRCS += vp8/common/reconinter.c
VP8_COMMON_SRCS += vp8/common/reconintra.c
VP8_COMMON_SRCS += vp8/common/reconintra4x4.c
VP8_COMMON_SRCS += vp8/common/setupintrarecon.c
VP8_COMMON_SRCS += vp8/common/swapyv12buffer.c
VP8_COMMON_SRCS += vp8/common/treecoder.c
VP8_COMMON_SRCS += vp8/common/dequantize.c
VP8_COMMON_SRCS += vp8/common/idct_blk.c

# common (c)
VP8_COMMON_SRCS += vp8/common/arm/dequantize_arm.c
VP8_COMMON_SRCS += vp8/common/arm/arm_systemdependent.c
VP8_COMMON_SRCS += vp8/common/arm/bilinearfilter_arm.c
VP8_COMMON_SRCS += vp8/common/arm/filter_arm.c
VP8_COMMON_SRCS += vp8/common/arm/loopfilter_arm.c
VP8_COMMON_SRCS += vp8/common/arm/reconintra_arm.c

# common (armv6)
VP8_COMMON_SRCS += vp8/common/arm/armv6/intra4x4_predict_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/bilinearfilter_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/copymem8x4_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/copymem8x8_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/copymem16x16_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/dc_only_idct_add_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/iwalsh_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/filter_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/idct_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/loopfilter_v6$(ASM)
#VP8_COMMON_SRCS += vp8/common/arm/armv6/recon_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/simpleloopfilter_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/sixtappredict8x4_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/idct_blk_v6.c
VP8_COMMON_SRCS += vp8/common/arm/armv6/dequant_idct_v6$(ASM)
VP8_COMMON_SRCS += vp8/common/arm/armv6/dequantize_v6$(ASM)

# common (neon)
VP8_COMMON_SRCS += vp8/common/arm/neon/bilinearpredict4x4_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/bilinearpredict8x4_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/bilinearpredict8x8_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/bilinearpredict16x16_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/copymem8x4_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/copymem8x8_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/copymem16x16_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/dc_only_idct_add_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/iwalsh_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/loopfilter_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/loopfiltersimplehorizontaledge_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/loopfiltersimpleverticaledge_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/mbloopfilter_neon$(ASM).neon
#VP8_COMMON_SRCS += vp8/common/arm/neon/recon2b_neon$(ASM).neon
#VP8_COMMON_SRCS += vp8/common/arm/neon/recon4b_neon$(ASM).neon
#VP8_COMMON_SRCS += vp8/common/arm/neon/reconb_neon$(ASM).neon
#VP8_COMMON_SRCS += vp8/common/arm/neon/shortidct4x4llm_1_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/shortidct4x4llm_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/sixtappredict4x4_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/sixtappredict8x4_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/sixtappredict8x8_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/sixtappredict16x16_neon$(ASM).neon
#VP8_COMMON_SRCS += vp8/common/arm/neon/recon16x16mb_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/buildintrapredictorsmby_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/save_neon_reg$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/dequant_idct_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/idct_dequant_full_2x_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/idct_dequant_0_2x_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/dequantizeb_neon$(ASM).neon
VP8_COMMON_SRCS += vp8/common/arm/neon/idct_blk_neon.c.neon

### vp8dx_arm
VP8_DX_SRCS = vp8/decoder/arm/arm_dsystemdependent.c

#File list for armv6


### vp8dx
VP8_DX_SRCS += vp8/vp8_dx_iface.c
VP8_DX_SRCS += vp8/decoder/asm_dec_offsets.c
VP8_DX_SRCS += vp8/decoder/dboolhuff.c
VP8_DX_SRCS += vp8/decoder/decodemv.c
VP8_DX_SRCS += vp8/decoder/decodframe.c
VP8_DX_SRCS += vp8/decoder/detokenize.c
VP8_DX_SRCS += vp8/decoder/error_concealment.c
VP8_DX_SRCS += vp8/decoder/generic/dsystemdependent.c
VP8_DX_SRCS += vp8/decoder/reconintra_mt.c
VP8_DX_SRCS += vp8/decoder/threading.c
VP8_DX_SRCS += vp8/decoder/onyxd_if.c

### vpx_codec
API_SRCS = vpx/src/vpx_decoder.c
API_SRCS += vpx/src/vpx_decoder_compat.c
API_SRCS += vpx/src/vpx_encoder.c
API_SRCS += vpx/src/vpx_codec.c
API_SRCS += vpx/src/vpx_image.c
API_SRCS += vpx_scale/generic/scalesystemdependent.c

LOCAL_SRC_FILES = $(MEM_SRCS)
LOCAL_SRC_FILES += $(SCALE_SRCS)
LOCAL_SRC_FILES += $(VP8_CX_SRCS)
LOCAL_SRC_FILES += $(VP8_COMMON_SRCS)
LOCAL_SRC_FILES += $(VP8_DX_SRCS)
LOCAL_SRC_FILES += $(API_SRCS)

LOCAL_SRC_FILES += vpx_ports/arm_cpudetect.c

LOCAL_MODULE_CLASS := STATIC_LIBRARIES

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH) \
   	$(LOCAL_PATH)/vpx_ports \
	$(LOCAL_PATH)/vp8/common \
	$(LOCAL_PATH)/vp8/encoder \
	$(LOCAL_PATH)/vp8/decoder \
	$(LOCAL_PATH)/vp8 \
	$(LOCAL_PATH)/vpx_codec

LOCAL_STATIC_LIBRARIES += cpufeatures

include $(BUILD_STATIC_LIBRARY)
$(call import-module,android/cpufeatures)

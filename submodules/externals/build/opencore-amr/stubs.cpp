
#define AMRNB_WRAPPER_INTERNAL
#include <sp_dec.h>
#include <amrdecode.h>
#include <amrencode.h>
#include "interf_dec.h"
#include "interf_enc.h"

#include <dlfcn.h>

static Word16 (*sym_AMRDecode)(
        void *state_data,
        enum Frame_Type_3GPP  frame_type,
        UWord8 *speech_bits_ptr,
        Word16 *raw_pcm_buffer,
        bitstream_format input_format
);

static void (*sym_GSMDecodeFrameExit)(void **state_data);

static Word16 (*sym_GSMInitDecode)(void **state_data, Word8 *id);

static Word16 (*sym_AMREncodeInit)(
	void **pEncStructure,
	void **pSidSyncStructure,
	Flag dtx_enable);

static void (*sym_AMREncodeExit)(
        void **pEncStructure,
        void **pSidSyncStructure);

static Word16 (*sym_AMREncode)(
        void *pEncState,
        void *pSidSyncState,
        enum Mode mode,
        Word16 *pEncInput,
        UWord8 *pEncOutput,
        enum Frame_Type_3GPP *p3gpp_frame_type,
        Word16 output_format);

extern "C"{

Word16 AMREncodeInit(
	void **pEncStructure,
	void **pSidSyncStructure,
	Flag dtx_enable){
	return sym_AMREncodeInit(pEncStructure,pSidSyncStructure,dtx_enable);
}

void AMREncodeExit(
        void **pEncStructure,
        void **pSidSyncStructure){
	return sym_AMREncodeExit(pEncStructure,pSidSyncStructure);
}

Word16 AMREncode(
        void *pEncState,
        void *pSidSyncState,
        enum Mode mode,
        Word16 *pEncInput,
        UWord8 *pEncOutput,
        enum Frame_Type_3GPP *p3gpp_frame_type,
        Word16 output_format){
	int err=sym_AMREncode(pEncState,pSidSyncState,mode,pEncInput,pEncOutput,p3gpp_frame_type,AMR_TX_WMF /*AMR_TX_IETF*/);
	/*IETF format not supported by versions of opencore amr up to android 2.3, thus we ask WMF and fix after*/
	/*both formats seems identical except the first byte.*/
	pEncOutput[0]=(*p3gpp_frame_type)<<3;
	return err;
}

Word16 AMRDecode(
        void *state_data,
        enum Frame_Type_3GPP  frame_type,
        UWord8 *speech_bits_ptr,
        Word16 *raw_pcm_buffer,
        bitstream_format input_format
){
	return sym_AMRDecode(state_data,frame_type,speech_bits_ptr,raw_pcm_buffer,input_format);
}

void GSMDecodeFrameExit(void **state_data){
	return sym_GSMDecodeFrameExit(state_data);
}

Word16 GSMInitDecode(void **state_data, Word8 *id){
	return sym_GSMInitDecode(state_data,id);
}

#define LOAD_SYMBOL(symbol) \
{ \
	*((void**)&sym_##symbol)=dlsym(handle,#symbol); \
	if (sym_##symbol==NULL) { \
		*missing=#symbol; \
		return -1; \
	} \
}

int opencore_amr_wrapper_init(const char **missing){
	void *handle=dlopen("libstagefright.so",RTLD_GLOBAL);
	if (handle==NULL){
		*missing="libstagefright.so";
		return -1;
	}
	LOAD_SYMBOL(AMRDecode);
	LOAD_SYMBOL(GSMDecodeFrameExit)
	LOAD_SYMBOL(GSMInitDecode);
	LOAD_SYMBOL(AMREncodeInit);
	LOAD_SYMBOL(AMREncodeExit);
	LOAD_SYMBOL(AMREncode);
	return 0;
}


}//end of extern "C"



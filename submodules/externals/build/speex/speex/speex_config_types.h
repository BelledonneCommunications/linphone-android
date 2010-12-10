#ifndef __SPEEX_TYPES_H__
#define __SPEEX_TYPES_H__

/* these are filled in by configure */
typedef short spx_int16_t;
typedef unsigned short spx_uint16_t;
typedef int spx_int32_t;
typedef unsigned int spx_uint32_t;

extern float ff_scalarproduct_float_neon  (const float *v1, const float *v2, int len);
extern float interpolate_product_single(const float *a, const float *b, unsigned int len, const spx_uint32_t oversample, float *frac);
#endif


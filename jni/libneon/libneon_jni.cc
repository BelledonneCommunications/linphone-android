/*
libneon_jni.cc
Copyright (C) 2013  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

#include <jni.h>

extern "C" {
#include <cpu-features.h>
#include <android/log.h>

static JavaVM *jvm=0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *ajvm, void *reserved)
{
	jvm=ajvm;
	return JNI_VERSION_1_2;
}

extern "C" jboolean Java_org_linphone_CpuUtils_hasNeon(JNIEnv* env, jobject thiz) {
	return android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON;
}
}

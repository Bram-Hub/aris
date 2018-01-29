#ifndef JNI_ARIS_UTIL
#define JNI_ARIS_UTIL
#include <jni.h>
#include "vec.h"
#include "typedef.h"

unsigned char * from_jni_str(JNIEnv * env, jstring str);
void cleanup_str(JNIEnv * env, jstring jnistr, const char * str);
vec_t * get_vec(JNIEnv * env, jobjectArray arr);

#endif //JNI_ARIS_UTIL

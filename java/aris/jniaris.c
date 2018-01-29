#include <jni.h>
#include "edu_rpi_aris_ArisNative.h"
#include "process.h"
#include "jniarisutil.h"
#include "string.h"
#include "vec.h"

unsigned char * from_jni_str(JNIEnv * env, jstring str) {
    const char * cstr = (*env)->GetStringUTFChars(env, str, 0);
    unsigned char * ustr = (unsigned char *) calloc(strlen(cstr) + 1, sizeof(char));
    strcpy(ustr, cstr);
    cleanup_str(env, str, cstr);
    return ustr;
}

void cleanup_str(JNIEnv * env, jstring jnistr, const char * str) {
    (*env)->ReleaseStringUTFChars(env, jnistr, str);
}

jstring to_jni_str(JNIEnv * env, const char * str) {
    return (*env)->NewStringUTF(env, str);
}

vec_t * get_vec(JNIEnv * env, jobjectArray arr) {
    jsize size = (*env)->GetArrayLength(env, arr);
    vec_t * vec = init_vec(sizeof(char *));
    for(int i = 0; i < (int) size; ++i) {
        jstring jnistr = (jstring) (*env)->GetObjectArrayElement(env, arr, (jsize) i);
        const char * sstr = from_jni_str(env, jnistr);
        if(sstr != NULL) {
            unsigned char * ustr = (unsigned char *) calloc(strlen(sstr) + 1, sizeof(char));
            strcpy(ustr, sstr);
            vec_str_add_obj(vec, ustr);
        }
        cleanup_str(env, jnistr, sstr);
    }
    return vec;
}

JNIEXPORT jstring JNICALL Java_edu_rpi_aris_ArisNative_process_1sentence (JNIEnv *env, jclass obj, jstring conclusion, jobjectArray premises, jstring jnirule, jobjectArray variables) {
    unsigned char * conc = from_jni_str(env, conclusion);
    unsigned char * rule = from_jni_str(env, jnirule);
    vec_t * prems = get_vec(env, premises);
    vec_t * vars = get_vec(env, variables);
    char * result = process(conc, prems, rule, vars, NULL);
    destroy_str_vec(prems);
    destroy_str_vec(vars);
    free(conc);
    free(rule);
    if(result == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, result);
}

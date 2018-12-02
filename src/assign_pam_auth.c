
#include <jni.h>
#include <security/pam_appl.h>
#include <string.h>
#include <stdlib.h>
#include "edu_rpi_aris_assign_server_auth_PAMLoginAuth.h"

struct auth_res {
    jboolean success;
    int code;
    const char * reason;
};

int login_conv(int num_msg, const struct pam_message **msg, struct pam_response **resp, void *appdata_ptr) {
    const char * password = (const char *) appdata_ptr;
    struct pam_response * response = (struct pam_response *) calloc(num_msg, sizeof(struct pam_response));
    resp[0] = response;
    for (int i = 0; i < num_msg; ++i) {
        if(msg[i]->msg_style == PAM_PROMPT_ECHO_OFF) {
            char * dst = (char *) calloc(strlen(password) + 1, sizeof(char));
            memcpy(dst, password, strlen(password) + 1);
            response[i].resp = dst;
            response[i].resp_retcode = 0;
        }
    }
    return 0;
}

struct auth_res login_auth_pam(const char * username, const char * password) {
    pam_handle_t *pamh=NULL;
    int retval;
    struct auth_res res;
    res.success = JNI_FALSE;
    struct pam_conv conv = {
        login_conv,
        (void *) password
    };
    retval = pam_start("login", username, &conv, &pamh);

    if(retval == PAM_SUCCESS)
        retval = pam_authenticate(pamh, PAM_DISALLOW_NULL_AUTHTOK);

    if(retval == PAM_SUCCESS) {
        res.success = JNI_TRUE;
        retval = pam_setcred(pamh, PAM_REINITIALIZE_CRED);
    }
    res.code = retval;
    res.reason = pam_strerror(pamh, retval);

    pam_end(pamh, retval);

    return res;
}

JNIEXPORT jobject JNICALL Java_edu_rpi_aris_assign_server_auth_PAMLoginAuth_pam_1authenticate (JNIEnv * env, jclass cl, jstring user, jstring pass) {
    const char *username = (*env)->GetStringUTFChars(env, user, NULL);
    const char *password = (*env)->GetStringUTFChars(env, pass, NULL);
    struct auth_res result = login_auth_pam(username, password);
    (*env)->ReleaseStringUTFChars(env, user, username);
    (*env)->ReleaseStringUTFChars(env, pass, password);
    jclass pam_result_cls = (*env)->FindClass(env, "edu/rpi/aris/assign/server/auth/PAMResponse");
    jfieldID success_fld = (*env)->GetFieldID(env, pam_result_cls, "success", "Z");
    jfieldID retval_fld = (*env)->GetFieldID(env, pam_result_cls, "retval", "I");
    jfieldID error_fld = (*env)->GetFieldID(env, pam_result_cls, "error", "Ljava/lang/String;");

    jobject pam_result = (*env)->AllocObject(env, pam_result_cls);

    (*env)->SetBooleanField(env, pam_result, success_fld, result.success);
    (*env)->SetIntField(env, pam_result, retval_fld, result.code);

    jstring error = (*env)->NewStringUTF(env, result.reason);

    (*env)->SetObjectField(env, pam_result, error_fld, error);

    return pam_result;
}

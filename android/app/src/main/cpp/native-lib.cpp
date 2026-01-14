#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_sigao_sigao_1voice_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from Sigao Core (C++)";
    return env->NewStringUTF(hello.c_str());
}

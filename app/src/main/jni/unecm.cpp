/**
 * Modified by vloz on 07/10/2015.
 */

/***************************************************************************/
/*
** UNECM - Decoder for ECM (Error Code Modeler) format.
** Version 1.0
** Copyright (C) 2002 Neill Corlett
**
** This program is free software; you can redistribute it and/or
** modify it under the terms of the GNU General Public License
** as published by the Free Software Foundation; either version 2
** of the License, or (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
/***************************************************************************/
/*
** Portability notes:
**
** - Assumes a 32-bit or higher integer size
** - No assumptions about byte order
** - No assumptions about struct packing
** - No unaligned memory access
*/
/***************************************************************************/

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>


JavaVM* g_jvm = NULL;
jclass  g_mainActivityClass;
pthread_t thread1;

enum MessageAction
{
    UPDATE_PROGRESS = 0,
    UPDATE_STATE = 1
};

enum TaskSate
{
    DONE = 0,
    ERROR = 1
};

/* Data types */
typedef unsigned char ecc_uint8;
typedef unsigned short ecc_uint16;
typedef unsigned int ecc_uint32;

class Unecm{
public:
    Unecm(char* inputFullPath){
        inputPath = inputFullPath;
    }

    ~Unecm(){
        free(outfilename);
        free(inputPath);
        pthread_exit(NULL);
    }

    void start(){
        JNIEnv *env = NULL;
        if(g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED)
            g_jvm->AttachCurrentThread(&env, NULL);

        eccedc_init();

        /*
        ** Figure out what the output filename should be
        */
        outfilename = (char *)malloc(strlen((char*)inputPath) - 3);
        memcpy(outfilename, (char*)inputPath, strlen((char*)inputPath) - 4);
        outfilename[strlen((char*)inputPath) - 4] = 0;

        /*
        ** Open both files
        */
        fin = fopen((char*)inputPath, "rb");
        if(!fin) {
            updateState(ERROR,"Couldn't open input file!");
            delete this;
            return;
            //return (*env)->NewStringUTF(env, "Couldn't open input file!");
        }
        fout = fopen(outfilename, "wb");
        if(!fout) {
            fclose(fin);
            updateState(ERROR,"Couldn't open output file!");
            delete this;
            return;
            // return (*env)->NewStringUTF(env, "Couldn't open input file!");
        }
        /*
        ** Decode
        */
        int result = unecmify(fin, fout);
        /*
        ** Close everything
        */
        fclose(fout);
        fclose(fin);
        g_jvm->DetachCurrentThread();
        delete this;
    }

private:
    FILE *fin, *fout;
    char* outfilename;
    char* inputPath;
    ecc_uint8 ecc_f_lut[256];
    ecc_uint8 ecc_b_lut[256];
    ecc_uint32 edc_lut[256];

    unsigned long mycounter, mycounter_total;



    ecc_uint32 edc_partial_computeblock(
            ecc_uint32  edc,
            const ecc_uint8  *src,
            ecc_uint16  size
    ) {
        while(size--) edc = (edc >> 8) ^ edc_lut[(edc ^ (*src++)) & 0xFF];
        return edc;
    }

    void edc_computeblock(
            const ecc_uint8  *src,
            ecc_uint16  size,
            ecc_uint8  *dest
    ) {
        ecc_uint32 edc = edc_partial_computeblock(0, src, size);
        dest[0] = (edc >>  0) & 0xFF;
        dest[1] = (edc >>  8) & 0xFF;
        dest[2] = (edc >> 16) & 0xFF;
        dest[3] = (edc >> 24) & 0xFF;
    }

    void ecc_computeblock(
            ecc_uint8 *src,
            ecc_uint32 major_count,
            ecc_uint32 minor_count,
            ecc_uint32 major_mult,
            ecc_uint32 minor_inc,
            ecc_uint8 *dest
    ) {
        ecc_uint32 size = major_count * minor_count;
        ecc_uint32 major, minor;
        for(major = 0; major < major_count; major++) {
            ecc_uint32 index = (major >> 1) * major_mult + (major & 1);
            ecc_uint8 ecc_a = 0;
            ecc_uint8 ecc_b = 0;
            for(minor = 0; minor < minor_count; minor++) {
                ecc_uint8 temp = src[index];
                index += minor_inc;
                if(index >= size) index -= size;
                ecc_a ^= temp;
                ecc_b ^= temp;
                ecc_a = ecc_f_lut[ecc_a];
            }
            ecc_a = ecc_b_lut[ecc_f_lut[ecc_a] ^ ecc_b];
            dest[major              ] = ecc_a;
            dest[major + major_count] = ecc_a ^ ecc_b;
        }
    }

    void eccedc_generate(ecc_uint8 *sector, int type) {
        ecc_uint32 i;
        switch(type) {
            case 1: /* Mode 1 */
                /* Compute EDC */
                edc_computeblock(sector + 0x00, 0x810, sector + 0x810);
                /* Write out zero bytes */
                for(i = 0; i < 8; i++) sector[0x814 + i] = 0;
                /* Generate ECC P/Q codes */
                ecc_generate(sector, 0);
                break;
            case 2: /* Mode 2 form 1 */
                /* Compute EDC */
                edc_computeblock(sector + 0x10, 0x808, sector + 0x818);
                /* Generate ECC P/Q codes */
                ecc_generate(sector, 1);
                break;
            case 3: /* Mode 2 form 2 */
                /* Compute EDC */
                edc_computeblock(sector + 0x10, 0x91C, sector + 0x92C);
                break;
        }
    }

    void ecc_generate(
            ecc_uint8 *sector,
            int        zeroaddress
    ) {
        ecc_uint8 address[4], i;
        /* Save the address and zero it out */
        if(zeroaddress) for(i = 0; i < 4; i++) {
                address[i] = sector[12 + i];
                sector[12 + i] = 0;
            }
        /* Compute ECC P code */
        ecc_computeblock(sector + 0xC, 86, 24,  2, 86, sector + 0x81C);
        /* Compute ECC Q code */
        ecc_computeblock(sector + 0xC, 52, 43, 86, 88, sector + 0x8C8);
        /* Restore the address */
        if(zeroaddress) for(i = 0; i < 4; i++) sector[12 + i] = address[i];
    }


    void resetcounter(unsigned long total) {
        mycounter = 0;
        mycounter_total = total;
    }

    void setcounter(unsigned long n) {
        if((n >> 20) != (mycounter >> 20)) {
            unsigned long a = (n+64)/128;
            unsigned long d = (mycounter_total+64)/128;
            if(!d) d = 1;
            updateProgress((100*a) / d);
        }
        mycounter = n;
    }

    int unecmify(FILE *in, FILE *out) {
        unsigned checkedc = 0;
        unsigned char sector[2352];
        unsigned type;
        unsigned num;
        fseek(in, 0, SEEK_END);
        resetcounter(ftell(in));
        fseek(in, 0, SEEK_SET);
        if(
                (fgetc(in) != 'E') ||
                (fgetc(in) != 'C') ||
                (fgetc(in) != 'M') ||
                (fgetc(in) != 0x00)
                ) {
            //Header not found!
            goto corrupt;
        }
        for(;;) {
            int c = fgetc(in);
            int bits = 5;
            if(c == EOF) goto uneof;
            type = c & 3;
            num = (c >> 2) & 0x1F;
            while(c & 0x80) {
                c = fgetc(in);
                if(c == EOF) goto uneof;
                num |= ((unsigned)(c & 0x7F)) << bits;
                bits += 7;
            }
            if(num == 0xFFFFFFFF) break;
            num++;
            if(num >= 0x80000000) goto corrupt;
            if(!type) {
                while(num) {
                    int b = num;
                    if(b > 2352) b = 2352;
                    if(fread(sector, 1, b, in) != b) goto uneof;
                    checkedc = edc_partial_computeblock(checkedc, sector, b);
                    fwrite(sector, 1, b, out);
                    num -= b;
                    setcounter(ftell(in));
                }
            } else {
                while(num--) {
                    memset(sector, 0, sizeof(sector));
                    memset(sector + 1, 0xFF, 10);
                    switch(type) {
                        case 1:
                            sector[0x0F] = 0x01;
                            if(fread(sector + 0x00C, 1, 0x003, in) != 0x003) goto uneof;
                            if(fread(sector + 0x010, 1, 0x800, in) != 0x800) goto uneof;
                            eccedc_generate(sector, 1);
                            checkedc = edc_partial_computeblock(checkedc, sector, 2352);
                            fwrite(sector, 2352, 1, out);
                            setcounter(ftell(in));
                            break;
                        case 2:
                            sector[0x0F] = 0x02;
                            if(fread(sector + 0x014, 1, 0x804, in) != 0x804) goto uneof;
                            sector[0x10] = sector[0x14];
                            sector[0x11] = sector[0x15];
                            sector[0x12] = sector[0x16];
                            sector[0x13] = sector[0x17];
                            eccedc_generate(sector, 2);
                            checkedc = edc_partial_computeblock(checkedc, sector + 0x10, 2336);
                            fwrite(sector + 0x10, 2336, 1, out);
                            setcounter(ftell(in));
                            break;
                        case 3:
                            sector[0x0F] = 0x02;
                            if(fread(sector + 0x014, 1, 0x918, in) != 0x918) goto uneof;
                            sector[0x10] = sector[0x14];
                            sector[0x11] = sector[0x15];
                            sector[0x12] = sector[0x16];
                            sector[0x13] = sector[0x17];
                            eccedc_generate(sector, 3);
                            checkedc = edc_partial_computeblock(checkedc, sector + 0x10, 2336);
                            fwrite(sector + 0x10, 2336, 1, out);
                            setcounter(ftell(in));
                            break;
                    }
                }
            }
        }
        if(fread(sector, 1, 4, in) != 4) goto uneof;
        if(
                (sector[0] != ((checkedc >>  0) & 0xFF)) ||
                (sector[1] != ((checkedc >>  8) & 0xFF)) ||
                (sector[2] != ((checkedc >> 16) & 0xFF)) ||
                (sector[3] != ((checkedc >> 24) & 0xFF))
                ) {
            updateState(ERROR,"EDC error!");
            /*fprintf(stderr, "EDC error (%08X, should be %02X%02X%02X%02X)\n",
                    checkedc,
                    sector[3],
                    sector[2],
                    sector[1],
                    sector[0]
            );*/
            goto corrupt;
        }
        updateState(DONE,"OK!");
        return 0;
        uneof:
        updateState(ERROR,"UNEXPECTED END OF FILE!");
        corrupt:
        updateState(ERROR,"CORRUPT ECM FILE!");
        return 1;
    }

    void eccedc_init(void) {
        ecc_uint32 i, j, edc;
        for(i = 0; i < 256; i++) {
            j = (i << 1) ^ (i & 0x80 ? 0x11D : 0);
            ecc_f_lut[i] = j;
            ecc_b_lut[i ^ j] = i;
            edc = i;
            for(j = 0; j < 8; j++) edc = (edc >> 1) ^ (edc & 1 ? 0xD8018001 : 0);
            edc_lut[i] = edc;
        }
    }

    void sendJNIMessage( enum MessageAction action, int intvalue, const char *strvalue){
        JNIEnv *env = NULL;
        if(g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED)
            g_jvm->AttachCurrentThread( &env, NULL);
        jstring jstr_input = env->NewStringUTF(inputPath);
        jstring jstr_strvalue = env->NewStringUTF(strvalue);
        jmethodID updateProgress = env->GetStaticMethodID(g_mainActivityClass, "messageFromJni", "(Ljava/lang/String;IILjava/lang/String;)V");
        env->CallStaticVoidMethod(g_mainActivityClass, updateProgress, jstr_input,action,intvalue,jstr_strvalue);
        env->DeleteLocalRef(jstr_input);
        env->DeleteLocalRef(jstr_strvalue);

    }

    void updateProgress(int percent){
        sendJNIMessage(UPDATE_PROGRESS,percent, (char *) "");
    }

    void updateState(enum TaskSate state, const char *message){
        sendJNIMessage(UPDATE_STATE, state,message);
    }

};

extern "C"{
void *main_thread(void* args){
    Unecm* unecm = new Unecm((char *) args);
    unecm->start();
}

JNIEXPORT jstring JNICALL
Java_fr_bobenrieth_unecm_MainActivity_unecm(JNIEnv *env, jobject instance, jstring inputFullPath_) {

    const char *inputFullPath = env->GetStringUTFChars(inputFullPath_, 0);

    // Copying and releasing java string content to a regular heap char*
    char* args =(char*) malloc(strlen(inputFullPath)+1);
    memcpy(args, inputFullPath, strlen(inputFullPath));
    args[strlen(inputFullPath)]=0;

    env->ReleaseStringUTFChars( inputFullPath_, inputFullPath);


    if(pthread_create(&thread1, NULL, main_thread, args))
        return env->NewStringUTF( "Task Launched!");
    else
        return env->NewStringUTF("Error, couldn't create thread.");
}



jint JNI_OnLoad(JavaVM* vm, void * reserved)
{
    g_jvm = vm;

    JNIEnv* env;
    //*g_jvm)->GetEnv(g_jvm,env,JNI_VERSION_1_6);
    g_jvm->AttachCurrentThread (&env, NULL);
    jclass tmpClass = env->FindClass("fr/bobenrieth/unecm/MainActivity");
    g_mainActivityClass = (jclass)env->NewGlobalRef(tmpClass);
    //(*g_jvm)->DetachCurrentThread(g_jvm);

    return JNI_VERSION_1_6;
}


};




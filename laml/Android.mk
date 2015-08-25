LOCAL_PATH := $(call my-dir)

# Build static laml library
include $(CLEAR_VARS)
LOCAL_SRC_FILES := \
	$(call all-java-files-under, src) \
    $(call all-Iaidl-files-under, src)
LOCAL_AIDL_INCLUDES:= src
LOCAL_JAVA_LIBRARIES:= lewa-framework
LOCAL_MODULE:= laml
include $(BUILD_STATIC_JAVA_LIBRARY)

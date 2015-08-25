LOCAL_PATH := $(call my-dir)

# Launcher package
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += laml
LOCAL_STATIC_JAVA_LIBRARIES += listviewanimations
LOCAL_STATIC_JAVA_LIBRARIES += dom
LOCAL_STATIC_JAVA_LIBRARIES += lewa-download-manager
LOCAL_STATIC_JAVA_LIBRARIES += lewa-support-v7-appcompat
LOCAL_STATIC_JAVA_LIBRARIES += com.lewa.themes
LOCAL_JNI_SHARED_LIBRARIES := liblewa_imageutils
LOCAL_JNI_SHARED_LIBRARIES += liblewa_shell

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
                      vendor/lewa/apps/LewaSupportLib/actionbar_4.4/res \

LOCAL_AAPT_FLAGS := --auto-add-overlay \
                    --extra-packages lewa.support.v7.appcompat

LOCAL_ASSET_DIR := $(LOCAL_PATH)/assets

#include $(LOCAL_PATH)/version.mk
                               
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3

LOCAL_MANIFEST_PACKAGE_NAME := com.lewa.launcher
LOCAL_PACKAGE_NAME := LewaLauncherX
LOCAL_CERTIFICATE  := platform

include $(BUILD_PACKAGE)

# Declare static jar listviewanimations
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := listviewanimations:libs/listviewanimations.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += dom:libs/dom4j-1.6.1.jar
include $(BUILD_MULTI_PREBUILT)

# Build sub-modules
include $(call all-makefiles-under, $(LOCAL_PATH))

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_RRO_THEME := IconShapeTaperedRect
LOCAL_PACKAGE_NAME := IconShapeTaperedRectOverlay
LOCAL_SDK_VERSION := current
LOCAL_PRODUCT_MODULE := true

include $(BUILD_RRO_PACKAGE)


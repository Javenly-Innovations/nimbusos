PRODUCT_PACKAGES += \
    FossifyFileManager \
    FossifyCamera \
    FossifyCalculator \
    FossifyVoiceRecorder \
    FossifyDocuments \
    FossifyClock \
    FossifyGallery \
    FossifyMessages \
    FossifyPhone \
    FossifyNotes \
    FossifyCalendar \
    FossifyContacts \
    Aptoide

PRODUCT_PACKAGES -= \
    Calculator2 \
    DeskClock \
    Gallery3D \
    Messaging \
    Dialer \
    Camera2 \
    Recorder

PRODUCT_PACKAGE_OVERLAYS := \
    $(PRODUCT_PACKAGE_OVERLAYS) \
    vendor/nimbus/overlay/common

# SQLCipher loads native code and uses JNI callbacks.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.** { *; }
# Room entities are read by generated code.
-keep class com.apenkoder.niasfood.data.local.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

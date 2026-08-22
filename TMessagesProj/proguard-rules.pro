-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-keep class org.webrtc.* { *; }
-keep class org.webrtc.audio.* { *; }
-keep class org.webrtc.voiceengine.* { *; }
-keep class org.telegram.messenger.* { *; }
-keep class org.telegram.messenger.camera.* { *; }
-keep class org.telegram.messenger.secretmedia.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.time.* { *; }
-keep class org.telegram.messenger.video.* { *; }
-keep class org.telegram.messenger.voip.* { *; }
-keep class org.telegram.SQLite.** { *; }
-keep class org.telegram.tgnet.ConnectionsManager { *; }
-keep class org.telegram.tgnet.NativeByteBuffer { *; }
-keep class org.telegram.tgnet.RequestTimeDelegate { *; }
-keep class org.telegram.tgnet.RequestDelegate { *; }
-keep class com.google.android.exoplayer2.ext.** { *; }
-keep class com.google.android.exoplayer2.extractor.FlacStreamMetadata { *; }
-keep class com.google.android.exoplayer2.metadata.flac.PictureFrame { *; }
-keep class com.google.android.exoplayer2.decoder.SimpleDecoderOutputBuffer { *; }
-keep class org.telegram.ui.Stories.recorder.FfmpegAudioWaveformLoader { *; }
-keep class androidx.mediarouter.app.MediaRouteButton { *; }
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

# https://developers.google.com/ml-kit/known-issues#android_issues
-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }

# Constant folding for resource integers may mean that a resource passed to this method appears to be unused. Keep the method to prevent this from happening.
-keep class com.google.android.exoplayer2.upstream.RawResourceDataSource {
  public static android.net.Uri buildRawResourceUri(int);
}

# Methods accessed via reflection in DefaultExtractorsFactory
-dontnote com.google.android.exoplayer2.ext.flac.FlacLibrary
-keepclassmembers class com.google.android.exoplayer2.ext.flac.FlacLibrary {

}

# Some members of this class are being accessed from native methods. Keep them unobfuscated.
-keep class com.google.android.exoplayer2.decoder.VideoDecoderOutputBuffer {
  *;
}

-dontnote com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer
-keepclassmembers class com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer {
  <init>(android.os.Handler, com.google.android.exoplayer2.audio.AudioRendererEventListener, com.google.android.exoplayer2.audio.AudioProcessor[]);
}
-dontnote com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer
-keepclassmembers class com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer {
  <init>(android.os.Handler, com.google.android.exoplayer2.audio.AudioRendererEventListener, com.google.android.exoplayer2.audio.AudioProcessor[]);
}
-dontnote com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer
-keepclassmembers class com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer {
  <init>(android.os.Handler, com.google.android.exoplayer2.audio.AudioRendererEventListener, com.google.android.exoplayer2.audio.AudioProcessor[]);
}

# Constructors accessed via reflection in DefaultExtractorsFactory
-dontnote com.google.android.exoplayer2.ext.flac.FlacExtractor
-keepclassmembers class com.google.android.exoplayer2.ext.flac.FlacExtractor {
  <init>();
}

# Constructors accessed via reflection in DefaultDownloaderFactory
-dontnote com.google.android.exoplayer2.source.dash.offline.DashDownloader
-keepclassmembers class com.google.android.exoplayer2.source.dash.offline.DashDownloader {
  <init>(android.net.Uri, java.util.List, com.google.android.exoplayer2.offline.DownloaderConstructorHelper);
}
-dontnote com.google.android.exoplayer2.source.hls.offline.HlsDownloader
-keepclassmembers class com.google.android.exoplayer2.source.hls.offline.HlsDownloader {
  <init>(android.net.Uri, java.util.List, com.google.android.exoplayer2.offline.DownloaderConstructorHelper);
}
-dontnote com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader
-keepclassmembers class com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader {
  <init>(android.net.Uri, java.util.List, com.google.android.exoplayer2.offline.DownloaderConstructorHelper);
}

# Constructors accessed via reflection in DownloadHelper
-dontnote com.google.android.exoplayer2.source.dash.DashMediaSource$Factory
-keepclasseswithmembers class com.google.android.exoplayer2.source.dash.DashMediaSource$Factory {
  <init>(com.google.android.exoplayer2.upstream.DataSource$Factory);
}
-dontnote com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory
-keepclasseswithmembers class com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory {
  <init>(com.google.android.exoplayer2.upstream.DataSource$Factory);
}
-dontnote com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource$Factory
-keepclasseswithmembers class com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource$Factory {
  <init>(com.google.android.exoplayer2.upstream.DataSource$Factory);
}

# Huawei Services
-keep class com.huawei.hianalytics.**{ *; }
-keep class com.huawei.updatesdk.**{ *; }
-keep class com.huawei.hms.**{ *; }

# Don't warn about checkerframework and Kotlin annotations
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

-keep class io.nano.tex.** {*;}

# JLatexMath: macro/atom classes are loaded reflectively by Class.forName
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

# Use -keep to explicitly keep any other classes shrinking would remove
-dontoptimize
-dontobfuscate

###############################################
#  Xray / libv2ray v26.6.22                   #
#  AndroidLibXrayLite                         #
###############################################

# Native методы (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Все классы Xray-core обёртки
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-keepclassmembers class libv2ray.** { *; }

# Go/gomobile bindings (внутренности libv2ray.aar)
-keep class go.** { *; }
-keep class seq.** { *; }
-keep class fmt.** { *; }
-keep class errors.** { *; }
-keep class runtime.** { *; }

# Основные API Xray v26.x
-keep class libv2ray.Libv2ray { *; }
-keep class libv2ray.CoreController { *; }
-keep interface libv2ray.CoreCallbackHandler { *; }
-keep interface libv2ray.ProcessFinder { *; }

# Callback реализация
-keepclassmembers class * implements libv2ray.CoreCallbackHandler { *; }

-dontwarn go.**
-dontwarn seq.**
-dontwarn libv2ray.**

###############################################
#  WorkgramXray — наши классы                 #
###############################################

-keep class org.telegram.messenger.Controller { *; }
-keep class org.telegram.messenger.XrayConfig { *; }
-keep class org.telegram.messenger.XrayLite { *; }
-keep class org.telegram.messenger.XrayLite$* { *; }
-keep class org.telegram.messenger.XrayRuntime { *; }

# === YANDEX BANNER CHANNEL: fix NoClassDefFoundError on PagerSnapHelper ===
# DivKit (used by Yandex Mobile Ads for rich/carousel banner creatives) accesses
# these classes reflectively at runtime; without -keep, R8 strips them as
# "unused" even though our local androidx.recyclerview.widget.PagerSnapHelper.java
# source exists in the project, causing a crash when a rich ad creative loads.
-keep class androidx.recyclerview.widget.** { *; }
-keep class com.yandex.div.** { *; }
-keep class com.yandex.mobile.ads.** { *; }
-dontwarn com.yandex.div.**
-dontwarn com.yandex.mobile.ads.**
# === END ===

package org.morok.camera;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Range;

import org.morok.settings.MorokSettings;
import org.morok.settings.RoundVideoSettings;
import org.telegram.messenger.FileLog;

/** Resolves an experimental profile against the actual camera frame and AVC encoder. */
public final class RoundVideoQuality {
    public static final int FRAME_RATE = 30;

    private RoundVideoQuality() {}

    public static int desiredCaptureSize(int upstreamResolution) {
        try {
            return MorokSettings.roundVideo().desiredResolution(upstreamResolution);
        } catch (RuntimeException unavailableSettings) {
            return upstreamResolution;
        }
    }

    public static boolean enhanced() {
        try {
            return MorokSettings.roundVideo().enhanced;
        } catch (RuntimeException unavailableSettings) {
            return false;
        }
    }

    public static Plan resolve(int upstreamResolution, int upstreamBitrateKbps,
                               int previewWidth, int previewHeight) {
        RoundVideoSettings settings;
        try {
            settings = MorokSettings.roundVideo();
        } catch (RuntimeException unavailableSettings) {
            return Plan.upstream(upstreamResolution, upstreamBitrateKbps);
        }
        if (!settings.enhanced) return Plan.upstream(upstreamResolution, upstreamBitrateKbps);

        int sourceShortSide = Math.min(previewWidth, previewHeight);
        int desired = settings.desiredResolution(upstreamResolution);
        int[] candidates = desired >= 720 ? new int[] {desired, 640, 480, upstreamResolution}
                : desired >= 640 ? new int[] {desired, 480, upstreamResolution}
                : new int[] {desired, upstreamResolution};
        for (int resolution : candidates) {
            if (resolution < upstreamResolution || resolution > sourceShortSide) continue;
            Codec codec = supportedAvcCodec(resolution);
            if (codec == null || codec.maxBitrate < upstreamBitrateKbps * 1000) continue;
            int bitrateKbps = settings.desiredBitrateKbps(upstreamBitrateKbps, resolution);
            bitrateKbps = Math.max((codec.minBitrate + 999) / 1000,
                    Math.min(bitrateKbps, codec.maxBitrate / 1000));
            Plan plan = new Plan(true, resolution, bitrateKbps,
                    resolution < desired, settings.profile, codec.name);
            FileLog.d("MOROK round video plan profile=" + plan.profile + " output="
                    + plan.resolution + " bitrateKbps=" + plan.bitrateKbps
                    + " preview=" + previewWidth + "x" + previewHeight
                    + " downgraded=" + plan.downgraded);
            return plan;
        }
        FileLog.d("MOROK round video falling back to upstream: no supported source/AVC combination for "
                + desired + " from " + previewWidth + "x" + previewHeight);
        return new Plan(false, upstreamResolution, upstreamBitrateKbps, true, settings.profile, null);
    }

    private static Codec supportedAvcCodec(int resolution) {
        try {
            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            for (MediaCodecInfo info : infos) {
                if (!info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (!"video/avc".equalsIgnoreCase(type)) continue;
                    MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type);
                    if (!contains(capabilities.colorFormats,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)) continue;
                    MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
                    if (!video.areSizeAndRateSupported(resolution, resolution, FRAME_RATE)) continue;
                    Range<Integer> range = video.getBitrateRange();
                    return new Codec(info.getName(), range == null ? 1 : range.getLower(),
                            range == null ? Integer.MAX_VALUE : range.getUpper());
                }
            }
        } catch (RuntimeException codecQueryFailed) {
            FileLog.e(codecQueryFailed);
        }
        return null;
    }

    private static boolean contains(int[] values, int expected) {
        if (values == null) return false;
        for (int value : values) if (value == expected) return true;
        return false;
    }

    private static final class Codec {
        final String name;
        final int minBitrate;
        final int maxBitrate;
        Codec(String name, int minBitrate, int maxBitrate) {
            this.name = name;
            this.minBitrate = minBitrate;
            this.maxBitrate = maxBitrate;
        }
    }

    public static final class Plan {
        public final boolean enhanced;
        public final int resolution;
        public final int bitrateKbps;
        public final boolean downgraded;
        public final String profile;
        public final String encoderName;

        Plan(boolean enhanced, int resolution, int bitrateKbps, boolean downgraded, String profile,
             String encoderName) {
            this.enhanced = enhanced;
            this.resolution = resolution;
            this.bitrateKbps = bitrateKbps;
            this.downgraded = downgraded;
            this.profile = profile;
            this.encoderName = encoderName;
        }

        static Plan upstream(int resolution, int bitrateKbps) {
            return new Plan(false, resolution, bitrateKbps, false, RoundVideoSettings.PROFILE_AUTO, null);
        }
    }
}

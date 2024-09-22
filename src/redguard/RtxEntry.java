package redguard;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import static modManager.RedguardModManager.logger;

public class RtxEntry {
    /*
    Notes for audio export:
    - Export in Audacity as WAV format, Mono channel, either 11025 Hz (lower quality) or 22050 Hz (higher quality) sample rate, and Signed 16-bit PCM encoding
     */
    private final String label;
    private String subtitle;
    private byte[] audioBytes;
    private AudioFormat audioFormat;

    public static final AudioFormat AUDIO_FORMAT_11025 = new AudioFormat(11025, 4, 1, false, false);
    public static final AudioFormat AUDIO_FORMAT_11025_FROM16BIT = new AudioFormat(11025, 8, 1, false, false);
    public static final AudioFormat AUDIO_FORMAT_22050 = new AudioFormat(22050, 8, 1, false, false);
    public static final AudioFormat AUDIO_FORMAT_22050_DOUBLE = new AudioFormat(22050, 16, 1, false, false);

    public RtxEntry(String label, String subtitle, byte[] audioBytes, int sampleRate, int doubleSize) {
        this.label = label;
        this.subtitle = subtitle;
        this.audioBytes = audioBytes;
        audioFormat = null;
        if (hasAudio()) {
            if (doubleSize == 1) {
                audioFormat = AUDIO_FORMAT_22050_DOUBLE;
            } else if (sampleRate == 22050) {
                audioFormat = AUDIO_FORMAT_22050;
            } else {
                audioFormat = AUDIO_FORMAT_11025;
            }
        }
    }

    public RtxEntry(String label, String subtitle) {
        this(label, subtitle, null, 11025, 0);
    }

    public String getLabel() {
        return label;
    }

    /**
     * Compute the total length of the dialogue record in the RTX file. 6 is for the label and "has audio" flag. The 27 is all the audio metadata.
     * @return Length of the dialogue record
     */
    public int length() {
        return 6 + subtitle.length() + (audioBytes == null ? 0 : 27 + audioBytes.length);
    }

    public int subtitleLength() {
        return subtitle.length();
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public byte[] getAudioBytes() {
        return audioBytes;
    }

    public boolean hasAudio() {
        return audioBytes != null;
    }

    public int getSampleRate() {
        return (int) audioFormat.getSampleRate();
    }

    public boolean getDoubleSize() {
        return audioFormat.getSampleRate() == 22050 && audioFormat.getSampleSizeInBits() == 16;
    }

    public AudioFormat externalAudioFormat() {
        return new AudioFormat(audioFormat.getSampleRate(), audioFormat.getSampleSizeInBits(), 1, true, false);
    }

    /**
     * Generate a new audio input stream based on the current audio bytes and format.
     * @return The new audio input stream
     */
    public AudioInputStream audioInputStream() {
        if (!hasAudio()) return null;
        if (audioFormat == AUDIO_FORMAT_22050) {
            AudioInputStream inputStream = new AudioInputStream(
                    new ByteArrayInputStream(audioBytes),
                    audioFormat,
                    audioBytes.length / audioFormat.getFrameSize()
            );
            return AudioSystem.getAudioInputStream(externalAudioFormat(), inputStream);
        }
        return new AudioInputStream(
                new ByteArrayInputStream(audioBytes),
                externalAudioFormat(),
                audioBytes.length / audioFormat.getFrameSize()
        );
    }

    /**
     * Play this entry's current audio bytes.
     * @return The clip object in case it needs to be stopped early
     * @throws IOException A general IO error occurred
     */
    public Clip playAudio() throws IOException {
        if (!hasAudio()) return null;
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream());
            clip.start(); // Start playing the clip
            return clip;
        } catch (LineUnavailableException e) {
            logger.warning("Audio line unavailable to play for " + label + ".");
        }
        return null;
    }

    /**
     * Load audio data from a file and replace the original audio bytes with it.
     * @param file The file with new audio data
     * @throws UnsupportedAudioFileException The audio data was invalid in some way
     * @throws IOException A general IO error occurred
     */
    public void loadAudioFromFile(File file) throws UnsupportedAudioFileException, IOException {
        // Convert to compatible format, choosing the higher sample rate only if provided
        AudioInputStream input = AudioSystem.getAudioInputStream(file);
        AudioFormat oldFormat = input.getFormat();
        if (oldFormat.getSampleRate() == 22050) {
            audioFormat = AUDIO_FORMAT_22050_DOUBLE;
        } else if (oldFormat.getSampleSizeInBits() == 16) {
            audioFormat = AUDIO_FORMAT_11025_FROM16BIT;
        } else {
            audioFormat = AUDIO_FORMAT_11025;
        }
        AudioInputStream convertedInput = AudioSystem.getAudioInputStream(externalAudioFormat(), input);

        // Save audio bytes
        int numBytes = (int) (convertedInput.getFrameLength() * audioFormat.getFrameSize());
        audioBytes = new byte[numBytes];
        if (convertedInput.read(audioBytes) != numBytes) {
            logger.warning("End of file reached early while reading " + numBytes + " bytes from audio input stream.");
        }
        input.close();
        convertedInput.close();
    }
}
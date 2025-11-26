package com.demo.cogbee.util;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FFmpegUtil {

	public static List<File> extractFrames(File videoFile, int intervalSeconds) {
		try {
			String outputPattern = videoFile.getParent() + "/frame_%03d.jpg";

			ProcessBuilder pb = new ProcessBuilder(
					"ffmpeg", "-i", videoFile.getAbsolutePath(),
					"-vf", "fps=1/" + intervalSeconds, outputPattern
			);
			pb.inheritIO();
			Process process = pb.start();
			process.waitFor();

			List<File> frames = new ArrayList<>();
			File parent = videoFile.getParentFile();
			for (File f : parent.listFiles((dir, name) -> name.startsWith("frame_"))) {
				frames.add(f);
			}
			return frames;
		} catch (Exception e) {
			throw new RuntimeException("Failed to extract frames: " + e.getMessage(), e);
		}
	}
}

package com.eharrison.canary.playerstate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class JarUtil {
	static public boolean exportResource(final String resourceName, final File targetDir)
			throws IOException {
		boolean success = false;
		
		final File targetFile = new File(targetDir, resourceName);
		if (!targetFile.exists()) {
			final InputStream in = JarUtil.class.getResourceAsStream("/" + resourceName);
			OutputStream out = null;
			if (in != null) {
				try {
					targetDir.mkdirs();
					targetFile.createNewFile();
					
					int readBytes;
					final byte[] buffer = new byte[1024];
					out = new FileOutputStream(targetFile);
					while ((readBytes = in.read(buffer)) > 0) {
						out.write(buffer, 0, readBytes);
					}
					
					PlayerStatePlugin.LOG.info("Wrote default PlayerState.cfg to "
							+ targetFile.getAbsolutePath());
					success = true;
				} finally {
					if (in != null) {
						in.close();
					}
					if (out != null) {
						out.close();
					}
				}
			}
		}
		
		return success;
	}
}

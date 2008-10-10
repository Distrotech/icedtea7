/* PulseAudioClip.java
   Copyright (C) 2008 Red Hat, Inc.

This file is part of IcedTea.

IcedTea is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 2.

IcedTea is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with IcedTea; see the file COPYING.  If not, write to
the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.
 */

package org.classpath.icedtea.pulseaudio;

import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioPermission;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;

import org.classpath.icedtea.pulseaudio.Stream.WriteListener;

public class PulseAudioClip extends PulseAudioDataLine implements Clip,
		PulseAudioPlaybackLine {

	private byte[] data = null;

	private boolean muted;
	private float volume;

	// these are frame indices. so counted from 0
	// the current frame index
	private int currentFrame = 0;

	// total number of frames in this clip
	private int frameCount = 0;

	// the starting frame of the loop
	private int startFrame = 0;
	// the ending frame of the loop
	private int endFrame = 0;

	public static final String DEFAULT_CLIP_NAME = "Clip";

	private Object clipLock = new Object();
	private int loopsLeft = 0;

	// private Semaphore clipSemaphore = new Semaphore(1);

	private class ClipThread extends Thread {
		@Override
		public void run() {

			/*
			 * The while loop below only works with LOOP_CONTINUOUSLY because we
			 * abuse the fact that loopsLeft's initial value is -1
			 * (=LOOP_CONTINUOUSLY) and it keeps on going lower without hitting
			 * 0. So do a sanity check
			 */
			if (Clip.LOOP_CONTINUOUSLY != -1) {
				throw new UnsupportedOperationException(
						"LOOP_CONTINUOUSLY has changed; things are going to break");
			}

			while (true) {
				writeFrames(currentFrame, endFrame + 1);
				if (Thread.interrupted()) {
					// Thread.currentThread().interrupt();
					// System.out.println("returned from interrupted
					// writeFrames");
					break;
				}

				// if loop(0) has been called from the mainThread,
				// wait until loopsLeft has been set
				if (loopsLeft == 0) {
					// System.out.println("Reading to the end of the file");
					// System.out.println("endFrame: " + endFrame);
					writeFrames(endFrame, getFrameLength());
					break;
				} else {
					synchronized (clipLock) {
						currentFrame = startFrame;
						if (loopsLeft != Integer.MIN_VALUE) {
							loopsLeft--;
						}
					}
				}

			}

			// drain
			Operation operation;

			synchronized (eventLoop.threadLock) {
				operation = stream.drain();
			}

			operation.waitForCompletion();
			operation.releaseReference();

		}
	}

	private ClipThread clipThread;

	private void writeFrames(int startingFrame, int lastFrame) {

		WriteListener writeListener = new WriteListener() {
			@Override
			public void update() {
				synchronized (eventLoop.threadLock) {
					eventLoop.threadLock.notifyAll();
				}
			}
		};

		stream.addWriteListener(writeListener);

		int remainingFrames = lastFrame - startingFrame - 1;
		while (remainingFrames > 0) {
			synchronized (eventLoop.threadLock) {
				int availableSize;

				do {
					availableSize = stream.getWritableSize();
					if (availableSize < 0) {
						Thread.currentThread().interrupt();
						stream.removeWriteListener(writeListener);
						return;
					}
					if (availableSize == 0) {
						try {
							eventLoop.threadLock.wait();
						} catch (InterruptedException e) {
							// System.out
							// .println("interrupted while waiting for
							// getWritableSize");
							// clean up and return
							Thread.currentThread().interrupt();
							stream.removeWriteListener(writeListener);
							return;
						}
					}

				} while (availableSize == 0);

				int framesToWrite = Math.min(remainingFrames, availableSize
						/ getFormat().getFrameSize());
				stream.write(data, currentFrame * getFormat().getFrameSize(),
						framesToWrite * getFormat().getFrameSize());
				remainingFrames -= framesToWrite;
				currentFrame += framesToWrite;
				framesSinceOpen += framesToWrite;
				if (Thread.interrupted()) {
					Thread.currentThread().interrupt();
					break;
				}
				// System.out.println("remaining frames" + remainingFrames);
				// System.out.println("currentFrame: " + currentFrame);
				// System.out.println("framesSinceOpen: " + framesSinceOpen);
			}
		}

		stream.removeWriteListener(writeListener);
	}

	public PulseAudioClip(EventLoop eventLoop, AudioFormat[] formats,
			AudioFormat defaultFormat) {
		supportedFormats = formats;
		this.eventLoop = eventLoop;
		this.defaultFormat = defaultFormat;
		this.currentFormat = defaultFormat;
		this.volume = PulseAudioVolumeControl.MAX_VOLUME;

		clipThread = new ClipThread();

	}

	protected void connectLine(int bufferSize, Stream masterStream)
			throws LineUnavailableException {
		StreamBufferAttributes bufferAttributes = new StreamBufferAttributes(
				bufferSize, bufferSize / 2, bufferSize / 2, bufferSize / 2, 0);

		if (masterStream != null) {
			synchronized (eventLoop.threadLock) {
				stream.connectForPlayback(Stream.DEFAULT_DEVICE,
						bufferAttributes, masterStream.getStreamPointer());
			}
		} else {
			synchronized (eventLoop.threadLock) {
				stream.connectForPlayback(Stream.DEFAULT_DEVICE,
						bufferAttributes, null);
			}
		}
	}

	@Override
	public int available() {
		return 0; // a clip always returns 0
	}

	@Override
	public void close() {

		/* check for permission to play audio */
		AudioPermission perm = new AudioPermission("play", null);
		perm.checkGuard(null);

		if (!isOpen) {
			throw new IllegalStateException("line already closed");
		}

		clipThread.interrupt();

		try {
			clipThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		currentFrame = 0;
		framesSinceOpen = 0;

		PulseAudioMixer mixer = PulseAudioMixer.getInstance();
		mixer.removeSourceLine(this);

		super.close();

	}

	/*
	 * 
	 * drain() on a Clip should block until the entire clip has finished playing
	 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4732218
	 * 
	 * 
	 * @see org.classpath.icedtea.pulseaudio.PulseAudioDataLine#drain()
	 */
	@Override
	public void drain() {
		if (!isOpen) {
			throw new IllegalStateException("line not open");
		}

		while (clipThread != null && clipThread.isAlive()) {
			try {
				clipThread.join();
			} catch (InterruptedException e) {
				// ignore
			}
		}

		Operation operation;

		synchronized (eventLoop.threadLock) {
			operation = stream.drain();
		}

		operation.waitForCompletion();
		operation.releaseReference();

	}

	@Override
	public void flush() {
		if (!isOpen) {
			throw new IllegalStateException("line not open");
		}

		Operation operation;
		synchronized (eventLoop.threadLock) {
			operation = stream.flush();
			operation.waitForCompletion();
		}
		operation.releaseReference();

	}

	@Override
	public int getFrameLength() {
		if (!isOpen) {
			return AudioSystem.NOT_SPECIFIED;
		}

		return frameCount;
	}

	@Override
	public int getFramePosition() {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}
		synchronized (clipLock) {
			return (int) framesSinceOpen;
		}
	}

	@Override
	public long getLongFramePosition() {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		synchronized (clipLock) {
			return framesSinceOpen;
		}
	}

	@Override
	public long getMicrosecondLength() {
		if (!isOpen) {
			return AudioSystem.NOT_SPECIFIED;
		}
		synchronized (clipLock) {
			return frameCount / currentFormat.getFrameSize();
		}
	}

	@Override
	public long getMicrosecondPosition() {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		synchronized (clipLock) {
			return framesSinceOpen / currentFormat.getFrameSize();
		}
	}

	@Override
	public void loop(int count) {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		if (count < 0 && count != LOOP_CONTINUOUSLY) {
			throw new IllegalArgumentException("invalid value for count:"
					+ count);
		}

		if (clipThread.isAlive() && count != 0) {
			// Do nothing; behavior not specified by the Java API
			return;
		}

		super.start();

		synchronized (clipLock) {
			if (currentFrame > endFrame) {
				loopsLeft = 0;
			} else {
				loopsLeft = count;
			}
		}
		if (!clipThread.isAlive()) {
			clipThread = new ClipThread();
			clipThread.start();
		}

	}

	@Override
	public void open() throws LineUnavailableException {
		throw new IllegalArgumentException("open() on a Clip is not allowed");
	}

	@Override
	public void open(AudioFormat format, byte[] data, int offset, int bufferSize)
			throws LineUnavailableException {

		/* check for permission to play audio */
		AudioPermission perm = new AudioPermission("play", null);
		perm.checkGuard(null);

		super.open(format);
		this.data = new byte[bufferSize];
		System.arraycopy(data, offset, this.data, 0, bufferSize);

		frameCount = bufferSize / format.getFrameSize();
		currentFrame = 0;
		framesSinceOpen = 0;
		startFrame = 0;
		endFrame = frameCount - 1;
		loopsLeft = 0;

		PulseAudioVolumeControl volumeControl = new PulseAudioVolumeControl(
				this, eventLoop);
		PulseAudioMuteControl muteControl = new PulseAudioMuteControl(this,
				volumeControl);
		controls.add(volumeControl);
		controls.add(muteControl);
		volume = volumeControl.getValue();
		muted = muteControl.getValue();

		PulseAudioMixer mixer = PulseAudioMixer.getInstance();
		mixer.addSourceLine(this);

		isOpen = true;

	}

	public byte[] native_setVolume(float value) {
		return stream.native_setVolume(value);
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean value) {
		muted = value;
	}

	public float getVolume() {
		return this.volume;
	}

	public void setVolume(float value) {
		this.volume = value;

	}

	@Override
	public void open(AudioInputStream stream) throws LineUnavailableException,
			IOException {
		byte[] buffer = new byte[(int) (stream.getFrameLength() * stream
				.getFormat().getFrameSize())];
		stream.read(buffer, 0, buffer.length);

		open(stream.getFormat(), buffer, 0, buffer.length);

	}

	@Override
	public void setFramePosition(int frames) {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		if (frames > frameCount) {
			throw new IllegalArgumentException("incorreft frame value");
		}

		synchronized (clipLock) {
			currentFrame = frames;
		}

	}

	@Override
	public void setLoopPoints(int start, int end) {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		if (end == -1) {
			end = frameCount - 1;
		}

		if (end < start) {
			throw new IllegalArgumentException(
					"ending point must be greater than or equal to the starting point");
		}

		synchronized (clipLock) {
			startFrame = start;
			endFrame = end;
		}

	}

	@Override
	public void setMicrosecondPosition(long microseconds) {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		float frameIndex = microseconds * currentFormat.getFrameRate();
		synchronized (clipLock) {
			currentFrame = (int) frameIndex;
		}

	}

	@Override
	public void start() {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		if (isStarted) {
			throw new IllegalStateException("already started");
		}

		super.start();

		if (!clipThread.isAlive()) {
			synchronized (clipLock) {
				loopsLeft = 0;
			}
			clipThread = new ClipThread();
			clipThread.start();
		}

	}

	public void stop() {
		if (!isOpen) {
			throw new IllegalStateException("Line not open");
		}

		if (!isStarted) {
			throw new IllegalStateException("not started, so cant stop");
		}

		if (clipThread.isAlive()) {
			clipThread.interrupt();
		}
		try {
			clipThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		synchronized (clipLock) {
			loopsLeft = 0;
		}

		super.stop();

	}
	
	public javax.sound.sampled.Line.Info getLineInfo() {
		return new DataLine.Info(Clip.class, supportedFormats,
				StreamBufferAttributes.MIN_VALUE,
				StreamBufferAttributes.MAX_VALUE);
	}

}

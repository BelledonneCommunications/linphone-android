/*
LinPhoneCallStatsImpl.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package org.linphone.core;


class LinphoneCallStatsImpl implements LinphoneCallStats {
	private int mediaType;
	private int iceState;
	private float downloadBandwidth;
	private float uploadBandwidth;
	private float senderLossRate;
	private float receiverLossRate;
	private float senderInterarrivalJitter;
	private float receiverInterarrivalJitter;
	private float roundTripDelay;
	private long latePacketsCumulativeNumber;
	private float jitterBufferSize;

	private native int getMediaType(long nativeStatsPtr);
	private native int getIceState(long nativeStatsPtr);
	private native float getDownloadBandwidth(long nativeStatsPtr);
	private native float getUploadBandwidth(long nativeStatsPtr);
	private native float getSenderLossRate(long nativeStatsPtr);
	private native float getReceiverLossRate(long nativeStatsPtr);
	private native float getSenderInterarrivalJitter(long nativeStatsPtr, long nativeCallPtr);
	private native float getReceiverInterarrivalJitter(long nativeStatsPtr, long nativeCallPtr);
	private native float getRoundTripDelay(long nativeStatsPtr);
	private native long getLatePacketsCumulativeNumber(long nativeStatsPtr, long nativeCallPtr);
	private native float getJitterBufferSize(long nativeStatsPtr);

	protected LinphoneCallStatsImpl(long nativeCallPtr, long nativeStatsPtr) {
		mediaType = getMediaType(nativeStatsPtr);
		iceState = getIceState(nativeStatsPtr);
		downloadBandwidth = getDownloadBandwidth(nativeStatsPtr);
		uploadBandwidth = getUploadBandwidth(nativeStatsPtr);
		senderLossRate = getSenderLossRate(nativeStatsPtr);
		receiverLossRate = getReceiverLossRate(nativeStatsPtr);
		senderInterarrivalJitter = getSenderInterarrivalJitter(nativeStatsPtr, nativeCallPtr);
		receiverInterarrivalJitter = getReceiverInterarrivalJitter(nativeStatsPtr, nativeCallPtr);
		roundTripDelay = getRoundTripDelay(nativeStatsPtr);
		latePacketsCumulativeNumber = getLatePacketsCumulativeNumber(nativeStatsPtr, nativeCallPtr);
		jitterBufferSize = getJitterBufferSize(nativeStatsPtr);
	}

	public MediaType getMediaType() {
		return MediaType.fromInt(mediaType);
	}

	public IceState getIceState() {
		return IceState.fromInt(iceState);
	}

	public float getDownloadBandwidth() {
		return downloadBandwidth;
	}

	public float getUploadBandwidth() {
		return uploadBandwidth;
	}

	public float getSenderLossRate() {
		return senderLossRate;
	}

	public float getReceiverLossRate() {
		return receiverLossRate;
	}

	public float getSenderInterarrivalJitter() {
		return senderInterarrivalJitter;
	}

	public float getReceiverInterarrivalJitter() {
		return receiverInterarrivalJitter;
	}

	public float getRoundTripDelay() {
		return roundTripDelay;
	}

	public long getLatePacketsCumulativeNumber() {
		return latePacketsCumulativeNumber;
	}

	public float getJitterBufferSize() {
		return jitterBufferSize;
	}
}

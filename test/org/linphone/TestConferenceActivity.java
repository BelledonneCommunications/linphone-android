/*
TestIncallCalleeBoxes.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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
package org.linphone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.OnlineStatus;
import org.linphone.core.PayloadType;
import org.linphone.core.VideoSize;
import org.linphone.core.LinphoneCall.State;

import android.os.Bundle;
import android.os.Handler;

/**
 * @author Guillaume Beraudo
 */
public class TestConferenceActivity extends IncallActivity {

	private Handler mHandler = new Handler();
	private LinphoneCoreTest mTestLc;
	protected final LinphoneCore lc() {return mTestLc;}

	private void simulateCallAccepted(final LinphoneCall call, int millis) {
		mHandler.postDelayed(new Runnable() {
			public void run() {
				lc().pauseAllCalls();
				((LinphoneCallTest)call).state = State.StreamsRunning;
				onCallStateChanged(call, call.getState(), "simulated out call answered");
			}
		}, millis);
	}
	private void delayedCallEvent(final LinphoneCall call, final State targetState, int millis) {
		mHandler.postDelayed(new Runnable() {
			public void run() {
				((LinphoneCallTest)call).state = targetState;
				onCallStateChanged(call, call.getState(), "simulated delayed state change " + targetState);
			}
		}, millis);
	}

	protected final List<LinphoneCall> getInitialCalls() {
		List<LinphoneCall> calls = new ArrayList<LinphoneCall>();
		int duration=0;
		mTestLc = new LinphoneCoreTest(calls);
		calls.add(new LinphoneCallTest(duration++, "Tartampion", "06.25.45.98.54", State.StreamsRunning));
		calls.add(new LinphoneCallTest(duration++, "Durand", "durand@sip.linphone.org", State.StreamsRunning));
		//	calls.add(new LinphoneCallTest(duration++, "Poupoux", "01.58.68.75.32", State.StreamsRunning));
		calls.add(new LinphoneCallTest(duration++, "Tante Germaine", "+33 1.58.68.75.32", State.Paused));
		//	calls.add(new LinphoneCallTest(duration++, "M. Le président ", "3615 Elysée", State.Paused));
		calls.add(new LinphoneCallTest(duration++, "01.58.68.75.32", "01.58.68.75.32", State.StreamsRunning));
		calls.add(new LinphoneCallTest(duration++, "A ringing out guy", "out-ringing@sip.linphone.org", State.OutgoingRinging));
		calls.add(new LinphoneCallTest(duration++, "A calling in guy", "in@sip.linphone.org", State.IncomingReceived));

		((LinphoneCallTest)calls.get(0)).inConf=true;
		((LinphoneCallTest)calls.get(1)).inConf=true;

		simulateCallAccepted(calls.get(4), 5000);
		Collections.sort(calls, this);

		mTestLc = new LinphoneCoreTest(calls);
		return calls;
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//if (!serviceStarted) startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));

		findViewById(R.id.toggleMuteMic).setOnClickListener(null);
		findViewById(R.id.toggleSpeaker).setOnClickListener(null);

		super.onCreate(savedInstanceState);
	}



	
	private class LinphoneCoreTest implements LinphoneCore {
		List<LinphoneCall> calls;
		public LinphoneCoreTest(List<LinphoneCall> calls) {
			this.calls = new ArrayList<LinphoneCall>(calls);
			//don't keep only the list reference (concurrent access in onStateChanged).
		}
		public void acceptCall(LinphoneCall call) throws LinphoneCoreException {
			if (isInConference()) {
				leaveConference();
			} else {
				LinphoneCall current = getCurrentCall();
				if (current != null) pauseCall(current);
			}
			changeState(call, State.StreamsRunning);
		}
		public void addAuthInfo(LinphoneAuthInfo info) {}
		public void addFriend(LinphoneFriend lf) throws LinphoneCoreException {}
		public void addProxyConfig(LinphoneProxyConfig p) throws LinphoneCoreException {}
		public void adjustSoftwareVolume(int i) {}
		public void clearAuthInfos() {}
		public void clearCallLogs() {}
		public void clearProxyConfigs() {}
		public LinphoneChatRoom createChatRoom(String to) {return null;}
		public LinphoneCallParams createDefaultCallParameters() {return null;}
		public void destroy() {}
		public void enableEchoCancellation(boolean enable) {}
		public void enableEchoLimiter(boolean val) {}
		public void enableIpv6(boolean enable) {}
		public void enableKeepAlive(boolean enable) {}
		public void enablePayloadType(PayloadType pt, boolean e)throws LinphoneCoreException {}
		public void enableSpeaker(boolean value) {}
		public void enableVideo(boolean vcapEnabled, boolean displayEnabled) {}
		public PayloadType findPayloadType(String mime, int clockRate) {return null;}
		public PayloadType[] getAudioCodecs() {return null;}
		@SuppressWarnings("unchecked")
		public List getCallLogs() {return null;}
		public LinphoneCall getCurrentCall() {
			LinphoneCall active = null;
			for (LinphoneCall call : calls) {
				if (call.isInConference() || !call.getState().equals(State.StreamsRunning)) continue;
				if (active != null) throw new RuntimeException("There are several active calls!");
				active = call;
			}
			return active;
		}
		public LinphoneProxyConfig getDefaultProxyConfig() {return null;}
		public FirewallPolicy getFirewallPolicy() {return null;}
		public int getPlayLevel() {return 0;}
		public float getPlaybackGain() {return 0;}
		public VideoSize getPreferredVideoSize() {return null;}
		public LinphoneAddress getRemoteAddress() {return null;}
		public String getRing() {return null;}
		public Transports getSignalingTransportPorts() {return null;}
		public String getStunServer() {return null;}
		public PayloadType[] getVideoCodecs() {return null;}
		public LinphoneAddress interpretUrl(String d)throws LinphoneCoreException {return null;}
		public LinphoneCall invite(String d) throws LinphoneCoreException {return null;}
		public LinphoneCall invite(LinphoneAddress to)throws LinphoneCoreException {return null;}
		public LinphoneCall inviteAddressWithParams(LinphoneAddress d, LinphoneCallParams p)
		throws LinphoneCoreException {return null;}
		public boolean isEchoCancellationEnabled() {return false;}
		public boolean isInComingInvitePending() {return false;}
		public boolean isIncall() {return false;}
		public boolean isKeepAliveEnabled() {return false;}
		public boolean isMicMuted() {return false;}
		public boolean isNetworkReachable() {return false;}
		public boolean isSpeakerEnabled() {return false;}
		public boolean isVideoEnabled() {return false;}
		public void iterate() {}
		public void muteMic(boolean isMuted) {}
		public boolean pauseAllCalls() {
			// FIXME may not be right
			for (LinphoneCall call : calls) {
				if (!call.isInConference()) {
					if (call.getState().equals(State.StreamsRunning) || call.getState().equals(State.PausedByRemote))
						pauseCall(call);
				}
			}
			return false;
		}
		public boolean pauseCall(LinphoneCall call) {
			changeState(call, State.Paused);
			return true;
		}
		public void playDtmf(char number, int duration) {}
		public boolean resumeCall(LinphoneCall call) {
			if (isInConference()) leaveConference();
			pauseAllCalls();
			changeState(call, State.StreamsRunning);
			return true;
		}
		public void sendDtmf(char number) {}
		public void setDefaultProxyConfig(LinphoneProxyConfig proxyCfg) {}
		public void setDownloadBandwidth(int bw) {}
		public void setDownloadPtime(int ptime) {}
		public void setFirewallPolicy(FirewallPolicy pol) {}
		public void setNetworkReachable(boolean isReachable) {}
		public void setPlayLevel(int level) {}
		public void setPlaybackGain(float gain) {}
		public void setPreferredVideoSize(VideoSize vSize) {}
		public void setPresenceInfo(int m, String a, OnlineStatus s) {}
		public void setPreviewWindow(Object w) {}
		public void setRing(String path) {}
		public void setRootCA(String path) {}
		public void setSignalingTransportPorts(Transports transports) {}
		public void setStunServer(String stunServer) {}
		public void setUploadBandwidth(int bw) {}
		public void setUploadPtime(int ptime) {}
		public void setVideoWindow(Object w) {}
		public void setZrtpSecretsCache(String file) {}
		public void startEchoCalibration(Object d)throws LinphoneCoreException {}
		public void stopDtmf() {}
		public void terminateCall(LinphoneCall call) {
			changeStateInConf(call, false);
			changeState(call, State.CallEnd);
		}
		public int updateCall(LinphoneCall call, LinphoneCallParams params) {return 0;}
		private boolean partOfConf;
		public void enterConference() {
			pauseAllCalls();
			partOfConf=true;
			hackTriggerConfStateUpdate(); // FIXME hack; should have an event?
		}
		public void leaveConference() {
			partOfConf=false;
			hackTriggerConfStateUpdate(); // FIXME hack; should have an event?
		}
		public boolean isInConference() {return partOfConf;}
		public int getConferenceSize() {
			int count=0;
			for (LinphoneCall c : calls) {
				if (c.isInConference()) count++;
			}
			return count;
		}
		public void addAllToConference() {
			for (LinphoneCall c : calls) {
				final LinphoneCall.State state = c.getState();
				boolean connectionEstablished = state == State.StreamsRunning || state == State.Paused || state == State.PausedByRemote;
				if (connectionEstablished) {
					changeState(c, State.StreamsRunning);
					changeStateInConf(c, true);
				}
			}
			enterConference();
		}
		public void addToConference(LinphoneCall call) {
			if (getConferenceSize() == 0) {
				addAllToConference();
			} else {
				boolean mergingActiveCall = call.equals(getCurrentCall());
				changeState(call, State.StreamsRunning);
				changeStateInConf(call, true);
				if (mergingActiveCall) enterConference();
			}
		}
		public void terminateConference() {
			leaveConference();
			for (LinphoneCall call : calls) {
				if (!call.isInConference()) continue;
				terminateCall(call);
			}
		}
		private void changeState(LinphoneCall call, State state) {
			((LinphoneCallTest)call).state=state;
			onCallStateChanged(call, state, "triggered by stub");
		}
		private void changeStateInConf(LinphoneCall call, boolean inConf) {
			((LinphoneCallTest)call).inConf=inConf;
			onCallStateChanged(call, call.getState(), "in conf state changed");
		}
		public int getCallsNb() {
			int count=0;
			for (LinphoneCall call : calls) {
				if (!State.CallEnd.equals(call.getState())) count++;
			}
			return count;
		}
		public void terminateAllCalls() {
			terminateConference();
			for(LinphoneCall call : calls) {
				if (!State.CallEnd.equals(call.getState())) terminateCall(call);
			}
		}
		@SuppressWarnings("unchecked")
		public List getCalls() {
			return new ArrayList<LinphoneCall>(calls);
		}
		public void removeFromConference(LinphoneCall call) {
			changeStateInConf(call, false);
			changeState(call, State.Paused);
		}
		public void transferCall(LinphoneCall call, String referTo) {
			terminateCall(call);
		}
		public void transferCallToAnother(LinphoneCall callToTransfer, LinphoneCall destination) {
			if (!State.Paused.equals(callToTransfer.getState())) {
				throw new RuntimeException("call to transfer should be paused first");
			}
			terminateCall(callToTransfer);
			delayedCallEvent(destination, State.CallEnd, 3000);
		}
		public int getVideoDevice() {return 0;}
		public void setDeviceRotation(int rotation) {}
		public void setVideoDevice(int id) {}
		@Override
		public LinphoneCall findCallFromUri(String uri) {
			for (LinphoneCall call : calls) {
				if (call.getRemoteAddress().asStringUriOnly().equals(uri)) {
					return call;
				}
			}
			return null;
		}
		@Override
		public int getMaxCalls() {
			return 10;
		}
		@Override
		public boolean isMyself(String uri) {
			return false;
		}
		@Override
		public boolean soundResourcesLocked() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public void setMaxCalls(int max) {
			// TODO Auto-generated method stub
			
		}
		@Override
		public String getMediaEncryption() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public boolean isMediaEncryptionMandatory() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public void setMediaEncryption(String menc) {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setMediaEncryptionMandatory(boolean yesno) {
			// TODO Auto-generated method stub
			
		}
		@Override
		public boolean isEchoLimiterEnabled() {
			// TODO Auto-generated method stub
			return false;
		}
	}


	private static class LinphoneAddressTest implements LinphoneAddress {
		private String displayName;
		private String number;
		public LinphoneAddressTest(String niceName, String number) {
			this.displayName = niceName;
			this.number = number;}
		public String asString() {return displayName;}
		public String asStringUriOnly() {return getUserName() + "@" + getDomain();}
		public String getDisplayName() {return displayName;}
		public String getDomain() {return "example.org";}
		public String getPort() {return "5060";}
		public int getPortInt() {return 5060;}
		public String getUserName() {return number;}
		public void setDisplayName(String name) {}
		public void setDomain(String domain) {}
		public void setPort(String port) {}
		public void setPortInt(int port) {}
		public void setUserName(String username) {}
		public void clean() {}
		@Override public String toString() {return displayName;}
	}



	private static class LinphoneCallTest implements LinphoneCall {
		private boolean inConf;
		private State state;
		private LinphoneAddress remoteAddress;
		private int duration;

		public LinphoneCallTest(int duration, String name, String number, State state) {
			this.duration = duration;
			this.state = state;
			remoteAddress = new LinphoneAddressTest(name, number);
		}

		public void enableCamera(boolean enabled) {}
		public void enableEchoCancellation(boolean enable) {}
		public void enableEchoLimiter(boolean enable) {}
		public String getAuthenticationToken() {return null;}
		public float getAverageQuality() {return 0;}
		public LinphoneCallLog getCallLog() {return null;}
		public LinphoneCallParams getCurrentParamsCopy() {return null;}
		public float getCurrentQuality() {return 0;}
		public CallDirection getDirection() {return null;}
		public int getDuration() {return duration;}
		public LinphoneAddress getRemoteAddress() {return remoteAddress;}
		public LinphoneCall getReplacedCall() {return null;}
		public State getState() {return state;}
		public boolean isAuthenticationTokenVerified() {return false;}
		public boolean isEchoCancellationEnabled() {return false;}
		public boolean isEchoLimiterEnabled() {return false;}
		public boolean isInConference() { return inConf;}
		public boolean cameraEnabled() {return false;}

		@Override
		public void setAuthenticationTokenVerified(boolean verified) {
			// TODO Auto-generated method stub
			
		}
	}


	@Override
	protected void registerLinphoneListener(boolean register) {
		// Do nothing (especially, don't call LinphoneManager!)
	}	
}



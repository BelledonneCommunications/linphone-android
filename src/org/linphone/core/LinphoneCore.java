/*
LinphoneCore.java
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

import java.util.List;


	
public interface LinphoneCore {
	/*
	 * linphone core states
	 */
	public enum 	GeneralState {
		  /* states for GSTATE_GROUP_POWER */
	GSTATE_POWER_OFF(0),        /* initial state */
	GSTATE_POWER_STARTUP(1),
	GSTATE_POWER_ON(2),
	GSTATE_POWER_SHUTDOWN(3),
		  /* states for GSTATE_GROUP_REG */
	GSTATE_REG_NONE(10),       /* initial state */
	GSTATE_REG_OK(11),
	GSTATE_REG_FAILED(12),
		  /* states for GSTATE_GROUP_CALL */
	GSTATE_CALL_IDLE(20),      /* initial state */
	GSTATE_CALL_OUT_INVITE(21),
	GSTATE_CALL_OUT_CONNECTED(22),
	GSTATE_CALL_IN_INVITE(23),
	GSTATE_CALL_IN_CONNECTED(24),
	GSTATE_CALL_END(25),
	GSTATE_CALL_ERROR(26),
	GSTATE_INVALID(27);
	private final int mValue;
	
	GeneralState(int value) {
		mValue = value;
	}
	public static GeneralState fromInt(int value) {
		for (GeneralState state: GeneralState.values()) {
			if (state.mValue == value) return state;
		}
		throw new RuntimeException("sate not found ["+value+"]");
	}
	}

	
	/**
	 * @param identity sip uri sip:jehan@linphone.org
	 * @param proxy  sip uri (sip:linphone.org)
	 * @param route optionnal sip usi (sip:linphone.org)
	 * @param register should be initiated
	 * @return
	 */
	public LinphoneProxyConfig createProxyConfig(String identity,String proxy,String route,boolean enableRegister) throws LinphoneCoreException;
	/**
	 * clear all added proxy config
	 */
	public void clearProxyConfigs();
	
	public void addProxyConfig(LinphoneProxyConfig proxyCfg) throws LinphoneCoreException;

	public void setDefaultProxyConfig(LinphoneProxyConfig proxyCfg);
	
	/**
	 * @return null if no default proxy config 
	 */
	public LinphoneProxyConfig getDefaultProxyConfig() ;
	
	/**
	 * clear all the added auth info
	 */
	void clearAuthInfos();
	
	void addAuthInfo(LinphoneAuthInfo info);
	
	public void invite(String uri);
	
	public void invite(LinphoneAddress to);
	
	public void terminateCall();
	/**
	 * get the remote address in case of in/out call
	 * @return null if no call engaged yet
	 */
	public LinphoneAddress getRemoteAddress();
	/**
	 *  
	 * @return  TRUE if there is a call running or pending.
	 */
	public boolean isIncall();
	/**
	 * 
	 * @return Returns true if in incoming call is pending, ie waiting for being answered or declined.
	 */
	public boolean isInComingInvitePending();
	public void iterate();
	/**
	 * Accept an incoming call.
	 *
	 * Basically the application is notified of incoming calls within the
	 * {@link LinphoneCoreListener#inviteReceived(LinphoneCore, String)} listener.
	 * The application can later respond positively to the call using
	 * this method.
	 */
	public void acceptCall();
	
	
	/**
	 * @return a list of LinphoneCallLog 
	 */
	public List<LinphoneCallLog> getCallLogs();
	
	/**
	 * This method is called by the application to notify the Linphone core library when network is reachable.
	 * Calling this method with true trigger Linphone to initiate a registration process for all proxy
	 * configuration with parameter register set to enable.
	 * This method disable the automatic registration mode. It means you must call this method after each network state changes
	 * @param network state  
	 *
	 */
	public void setNetworkStateReachable(boolean isReachable);
	/**
	 * destroy linphone core and free all underlying resources
	 */
	public void destroy();
	/**
	 * Allow to control play level before entering  sound card:  
	 * @param level in db
	 */
	public void setSoftPlayLevel(float gain);
	/**
	 * get play level before entering  sound card:  
	 * @return level in db
	 */
	public float getSoftPlayLevel();
	/**
	 *  Mutes or unmutes the local microphone.
	 * @param isMuted
	 */
	public void muteMic(boolean isMuted);
	/**
	 * Build an address according to the current proxy config. In case destination is not a sip uri, the default proxy domain is automatically appended
	 * @param destination
	 * @return
	 * @throws If no LinphonrAddress can be built from destination
	 */
	public LinphoneAddress interpretUrl(String destination) throws LinphoneCoreException;
	/**
	 * Initiate a dtmf signal if in call
	 * @param number
	 */
	public void sendDtmf(char number);
	/**
	 * 
	 */
	public void clearCallLogs();
}

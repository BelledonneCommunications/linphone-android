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

import java.io.File;
import java.net.URI;
	
public interface LinphoneCore {
	/*
	 * linphone core states
	 */
	interface 	GeneralState {
		  /* states for GSTATE_GROUP_POWER */
	static int GSTATE_POWER_OFF =0;        /* initial state */
	static int 	  GSTATE_POWER_STARTUP=1;
	static int 	  GSTATE_POWER_ON=2;
	static int 	  GSTATE_POWER_SHUTDOWN=3;
		  /* states for GSTATE_GROUP_REG */
	static int 	  GSTATE_REG_NONE=10;       /* initial state */
	static int 	  GSTATE_REG_OK=11;
	static int 	  GSTATE_REG_FAILED=12;
		  /* states for GSTATE_GROUP_CALL */
	static int 	  GSTATE_CALL_IDLE=20;      /* initial state */
	static int 	  GSTATE_CALL_OUT_INVITE=21;
	static int 	  GSTATE_CALL_OUT_CONNECTED=22;
	static int 	  GSTATE_CALL_IN_INVITE=23;
	static int 	  GSTATE_CALL_IN_CONNECTED=24;
	static int 	  GSTATE_CALL_END=25;
	static int 	  GSTATE_CALL_ERROR=26;
	static int 	  GSTATE_INVALID=27;
	/**
	 * get new state {@link:  }
	 */
	public int getNewState();
}

	
	public LinphoneProxyConfig createProxyConfig(URI identity,URI proxy,URI route);
	
	public void setDefaultProxyConfig(LinphoneProxyConfig proxyCfg);
	
	/**
	 * @return null if no default proxyconfig 
	 */
	public LinphoneProxyConfig getDefaultProxyConfig();
	
	void addAuthInfo(LinphoneAuthInfo info);
	
	public void invite(String url);
	
	public void iterate();
}

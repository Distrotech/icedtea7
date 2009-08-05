/* VoidPluginCallRequest -- represent Java-to-JavaScript requests
   Copyright (C) 2008  Red Hat

This file is part of IcedTea.

IcedTea is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

IcedTea is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with IcedTea; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
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
exception statement from your version. */


package sun.applet;


class PluginMessageHandlerWorker extends Thread {

	private boolean free = true;
	private int id;
	private String message = null;
	private SecurityManager sm;
	PluginStreamHandler streamHandler = null;

	public PluginMessageHandlerWorker(PluginStreamHandler streamHandler, int id, SecurityManager sm) {
		this.id = id;
		this.streamHandler = streamHandler;
		this.sm = sm;
	}

	public void setmessage(String message) {
		this.message = message;
	}

	public void run() {
		while (true) {

			if (message != null) {
				
			    PluginDebug.debug("Consumer thread " + id + " consuming " + message);
			    
				// ideally, whoever returns things object should mark it 
				// busy first, but just in case..
				busy();

				try {
					streamHandler.handleMessage(message);
				} catch (PluginException pe) {
					/*
					   catch the exception and DO NOTHING. The plugin should take over after 
					   this error and let the user know. We don't quit because otherwise the 
					   exception will spread to the rest of the applets which is a no-no
					 */ 
				}

				this.message = null;
				
				PluginDebug.debug("Consumption completed by consumer thread " + id);

	            // mark ourselves free again
				free();
				
			} else {
				
				// Sleep when there is nothing to do
			    try {
			        Thread.sleep(Integer.MAX_VALUE);
			        PluginDebug.debug("Consumer thread " + id + " sleeping...");
			    } catch (InterruptedException ie) {
			        PluginDebug.debug("Consumer thread " + id + " woken...");
			        // nothing.. someone woke us up, see if there 
			        // is work to do
			    }
			}
		}
	}
	
	
	
	public int getWorkerId() {
		return id;
	}

	public void busy() {
		this.free = false;
	}

	
	public void free() {
		this.free = true;
	}
	
	public boolean isFree() {
		return free;
	}
}
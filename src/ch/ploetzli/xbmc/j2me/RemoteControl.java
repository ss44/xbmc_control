package ch.ploetzli.xbmc.j2me;

import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;

import ch.ploetzli.xbmc.Logger;
import ch.ploetzli.xbmc.Utils;
import ch.ploetzli.xbmc.api.HttpApi;
import ch.ploetzli.xbmc.api.RecordSetConnection;
import ch.ploetzli.xbmc.api.StateListener;
import ch.ploetzli.xbmc.api.StateMonitor;

public class RemoteControl extends DatabaseSubMenu implements StateListener {
	/* Constants from key.h */
	static final int KEY_BUTTON_A = 256;
	static final int KEY_BUTTON_DPAD_UP = 270;
	static final int KEY_BUTTON_DPAD_DOWN = 271;
	static final int KEY_BUTTON_DPAD_LEFT = 272;
	static final int KEY_BUTTON_DPAD_RIGHT = 273;
	static Command tabCommand = new Command("Send Tab", Command.SCREEN, 20);
	static Command escapeCommand = new Command("Send Esc", Command.SCREEN, 20);
	static Command smallStepBackCommand = new Command("Small step back", Command.SCREEN, 20);
	static Command startScreenshotCommand = new Command("Enable screenhots", Command.SCREEN, 5);
	static Command stopScreenshotCommand = new Command("Stop screenshots", Command.SCREEN, 5);
	private RemoteControlCanvas canvas = null;
	private boolean screenshotMode = false;
	private ScreenshotFetcher fetcher = null;
	private Image screenshot = null;
	private boolean paused = true;
	private int canvasWidth, canvasHeight;
	
	public RemoteControl(String name) {
		super(name);
		pause();
	}

	protected Displayable constructDisplayable() {
		synchronized(this) {
			if(canvas == null) {
				canvas = new RemoteControlCanvas(name);
				addPrivateCommands(canvas);
				canvas.setCommandListener(this);
			}
		}
		return canvas;
	}
	
	protected synchronized void addPrivateCommands(Displayable d) {
		d.addCommand(tabCommand);
		d.addCommand(escapeCommand);
		d.addCommand(smallStepBackCommand);
		if(!screenshotMode) {
			d.addCommand(startScreenshotCommand);
		} else {
			d.addCommand(stopScreenshotCommand);
		}
		super.addPrivateCommands(d);
	}
	
	public void commandAction(Command cmd, Displayable d) {
		if(cmd == tabCommand) {
			sendKey(0xF009);
		} else if(cmd == escapeCommand) {
			sendKey(0xF01B);
		} else if(cmd == smallStepBackCommand) {
			sendAction(76);
		} else if(cmd == startScreenshotCommand) {
			setScreenshotMode(true);
		} else if(cmd == stopScreenshotCommand) {
			setScreenshotMode(false);
		} else {
			super.commandAction(cmd, d);
		}
	}
	
	private synchronized void setScreenshotMode(boolean screenshotMode) {
		if(screenshotMode == this.screenshotMode) 
			return; /* Nothing to do */
		
		if(canvas != null) {
			if(this.screenshotMode)
				canvas.removeCommand(stopScreenshotCommand);
			else
				canvas.removeCommand(startScreenshotCommand);
		}
		
		this.screenshotMode = screenshotMode;

		if(screenshotMode)
			canvas.addCommand(stopScreenshotCommand);
		else
			canvas.addCommand(startScreenshotCommand);

		if(fetcher != null) {
			fetcher.exit = true;
		}
		
		if(screenshotMode) {
			fetcher = new ScreenshotFetcher();
			fetcher.start();
		}
	}
	
	public void sendKey(int buttoncode) {
		send(buttoncode, true);
	}
	
	public void sendAction(int code) {
		send(code, false);
	}
	
	/**
	 * Send a key or action to the HTTP API.
	 * @param code The code to send
	 * @param sendKey If true, use SendKey(), otherwise use Action()
	 */
	public void send(final int code, final boolean sendKey) {
		if(fetcher != null) {
			fetcher.keyPressed();
		}
		
		final HttpApi api = getApi();
		new Thread() {
			public void run() {
				try {
					if(sendKey)
						api.sendKey(code);
					else
						api.action(code);
				} catch (Exception e) {
					Logger.getLogger().error(e);
				}
			}
		}.start();
	}

	private HttpApi getApi() {
		DatabaseTopMenu topMenu = getDatabaseTopMenu();
		if(topMenu == null)
			return null;
		HttpApi api = topMenu.getApi();
		if(api == null)
			return null;
		return api;
	}
	
	public void stateSynchronized() {
		if(canvas == null)
			constructDisplayable();
		if(fetcher != null)
			fetcher.singleUpdate();
		if(canvas != null)
			canvas.refresh();
	}

	public void valueChanged(String property, String newValue) {
		if(canvas == null)
			constructDisplayable();
		if(canvas != null)
			canvas.setValue(property, newValue);
	}
	
	private void unpause() {
		HttpApi api = getApi();
		if(api != null) {
			api.getStateMonitor().registerListener(this, StateMonitor.INTEREST_TIME);
		}
		paused = false;
	}
	
	private void pause() {
		HttpApi api = getApi();
		if(api != null) {
			api.getStateMonitor().registerListener(this, StateMonitor.INTEREST_BASIC);
		}
		paused = true;
	}
	
	protected class ScreenshotFetcher extends Thread {
		private long lastKeypress = 0;
		private long screenshotDelay = 1000;
		boolean exit = false;
		
		public void run() {
			try {
				HttpApi api = getApi();
				if(api == null)
					return;
				while(!exit) {
					synchronized(this) {
						if(!paused) {
							if(api != null) {
								try {
									int w = canvasWidth, h = canvasHeight;
									h = (w*9)/16; /* FIXME Fetch and use aspect ratio from HTTP API */
									byte data[] = api.takeScreenshot("special://temp/screen.jpg", false, 0, w, h, 50);
									screenshot = Image.createImage(data, 0, data.length);
								} catch (Exception e) {
									Logger.getLogger().error(e);
									e.printStackTrace();
								}
								if(canvas != null)
									canvas.drawScreenshot();
							}
						}

						long sinceLastKeypress = System.currentTimeMillis()-lastKeypress;
						if(sinceLastKeypress < 1000) {
							/* Don't touch delay */
						} else if(sinceLastKeypress < 5000) {
							screenshotDelay = 250;
						} else if(sinceLastKeypress < 10000) {
							screenshotDelay = 1000;
						} else if(sinceLastKeypress < 20000) {
							screenshotDelay = 5000;
						} else {
							screenshotDelay = 10000;
						}

						System.out.println(screenshotDelay);
						try {
							this.wait(screenshotDelay);
						} catch (InterruptedException e) {;}
					}

				}
			} catch(Exception e) { Logger.getLogger().error(e); }
		}
		
		public void singleUpdate() {
			synchronized(this) {
				this.notify();
			}
		}

		public void keyPressed() {
			synchronized(this) {
				lastKeypress = System.currentTimeMillis();
				screenshotDelay = 100;
				this.notify();
			}
		}
		
	}
	
	Font font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN , Font.SIZE_MEDIUM);
	
	protected abstract class GUIElement {
		protected boolean dirty = false;
		public abstract boolean updateValue(String name, String value);
		public abstract void fetch(HttpApi api, int width, int height);
		public abstract void paint(Graphics g, int width, int height);
		public void setDirty() {
			dirty = true;
		}
		public boolean getDirty() {
			return dirty;
		}
		public boolean sizeChanged(int width, int height) {
			setDirty();
			return getDirty();
		}
	}
	
	protected abstract class StringGUIElement extends GUIElement {
		String[] value;
		String[] fields;
		
		public StringGUIElement() {
			fields = getFieldNames();
			value = new String[fields.length];	
		}
		
		public boolean updateValue(String name, String newValue) {
			for(int i=0; i<fields.length; i++) {
				if(!name.equals(fields[i]))
					continue;
				if( value[i] == null && newValue == null)
					continue; /* Nothing to do */
				else if( value[i] != null || newValue != null || !value[i].equals(newValue)) { 
					value[i] = newValue;
					dirty = true;
				}
			}
			return dirty;
		}
		
		public abstract String[] getFieldNames();
	}
	
	protected abstract class IntegerGUIElement extends GUIElement {
		int value = getDefaultValue();
		
		public boolean updateValue(String name, String newValue) {
			if(!name.equals(getFieldName()))
				return dirty;
			int newVal = getDefaultValue();
			try {
				newVal = Integer.parseInt(newValue);
			} catch(Exception e) {;}
			if(value != newVal) {
				value = newVal;
				dirty = true;
			}
			return dirty;
		}
		
		public abstract int getDefaultValue();
		public abstract String getFieldName();
	}
	
	protected class TvshowThumb extends StringGUIElement {
		Image thumb = null;
		
		public TvshowThumb() { super(); }
		
		public String[] getFieldNames() {
			return new String[]{"Show Title"};
		}

		public void fetch(HttpApi api, int width, int height) {
			if(dirty) {
				thumb = null;
				if(api != null && value[0] != null) {
					try {
						RecordSetConnection conn = api.queryVideoDatabase("SELECT strPath,c00 FROM tvshowview WHERE c00 = '"+value[0]+"' LIMIT 1");
						String data[] = new String[]{};
						if(conn.hasMoreElements())
							data = (String[]) conn.nextElement();
						if(data.length > 0) {
							String crc = Utils.crc32(data[0]);
							thumb = ImageFactory.getRemoteImage(api, "special://userdata/Thumbnails/Video/"+ crc.charAt(0) + "/" + crc + ".tbn");
						}

						if(thumb != null) {
							/* maximum width: fullcreen - 2*10px border; maximum height: fullscreen - 10px border - 15px progress bar - title label - maximum height of FileThumb */
							thumb = ImageFactory.scaleImageToFit(thumb,	width-20, (int) (height-35-font.getHeight()-height*FileThumb.maxHeightFactor));
						}
						dirty = false;
					} catch (Exception e) {
						Logger.getLogger().error(e);
					}
				} else if(value[0] == null) {
					dirty = false;
				}
			}
		}

		public void paint(Graphics g, int width, int height) {
			if(thumb != null) {
				g.drawImage(thumb, width/2, 10, Graphics.TOP | Graphics.HCENTER);
			}
		}
		
	}

	protected class FileThumb extends StringGUIElement {
		Image thumb = null;
		static final double maxWidthFactor = 0.45;
		static final double maxHeightFactor = 0.42;
		
		public FileThumb() {super();}
		
		public String[] getFieldNames() {
			return new String[]{"Thumb", "Show Title"};
		}
		
		public void fetch(HttpApi api, int width, int height) {
			if(dirty) {
				thumb = null;
				if(api != null && value[0] != null) {
					try {
						thumb = ImageFactory.getRemoteImage(api, value[0]);

						if(thumb != null) {
							if(value[1] != null) {
								/* maximum width: fixed factor; maximum height: fixed factor */
								thumb = ImageFactory.scaleImageToFit(thumb, (int)(width*maxWidthFactor), (int)(height*maxHeightFactor));
							} else {
								/* maximum width: fixed factor; maximum height: fullscreen - 10px border - 15px progress bar - title label */
								thumb = ImageFactory.scaleImageToFit(thumb, (int)(width*maxWidthFactor), (height-35-font.getHeight()));
							}

						}
						dirty = false;
					} catch (Exception e) {
						Logger.getLogger().error(e);
					}
				} else if(value[0] == null) {
					dirty = false;
				}
			}
		}

		public void paint(Graphics g, int width, int height) {
			if(thumb != null) {
				if(value[1] != null) {
					/* x: left + 10px border + maximum width/2; y: bottom - 10px border - 15px progress bar - title label - maximum height/2 */
					g.drawImage(thumb, (int)(10+width*maxWidthFactor/2), (int)(height-25-font.getHeight()-height*maxHeightFactor/2), Graphics.VCENTER | Graphics.HCENTER);
				} else {
					/* x: left + 10px border + maximum width/2; y: top + 10px border + maximum height/2 */
					g.drawImage(thumb, (int)(10+width*maxWidthFactor/2), 10+(height-35-font.getHeight())/2, Graphics.VCENTER | Graphics.HCENTER);
				}
			}
		}
		
	}
	
	protected class PlaybackProgress extends IntegerGUIElement {

		public int getDefaultValue() {
			return -1;
		}

		public String getFieldName() {
			return "Percentage";
		}

		public void fetch(HttpApi api, int width, int height) {
			return;
		}

		public void paint(Graphics g, int width, int height) {
			int barWidth = 0;
			int maxWidth = width - 20;
			if(value > 100)
				value = 100;
			if(value < -1)
				value = -1;
			if(value != -1) {
				barWidth = (maxWidth * value) / 100;
				g.setColor(0, 0, 0);
				g.fillRoundRect(10, height-20, maxWidth, 10, 10, 10);
				g.setColor(31, 31, 31);
				g.drawRoundRect(10, height-20, maxWidth, 10, 10, 10);
				g.setColor(160, 160, 160);
				g.fillRoundRect(10, height-20, barWidth, 10, 10, 10);
				g.setColor(191, 191, 191);
				g.drawRoundRect(10, height-20, barWidth, 10, 10, 10);
				g.setColor(207, 207, 207);
				g.fillRoundRect(11, height-20+1, barWidth-2, 5, 4, 4);
			}
		}
		
	}
	
	protected class TitleLabel extends StringGUIElement {
		public TitleLabel() { super(); }
		
		public String[] getFieldNames() {
			return new String[]{"Title", "Filename"};
		}

		public void fetch(HttpApi api, int width, int height) {
		}

		public void paint(Graphics g, int width, int height) {
			if(value[0] != null || value[1] != null) {
				g.setColor(160, 160, 180);
				g.setFont(font);
				StringBuffer buf = new StringBuffer();
				if(value[0] != null)
					buf.append(value[0]);
				else if(value[1] != null)
					buf.append(value[1]);
				g.drawString(buf.toString(), 10, height-25, Graphics.LEFT | Graphics.BOTTOM);
			}
		}
	}
	
	protected class SeasonLabel extends StringGUIElement {
		public SeasonLabel() { super(); }
		
		public String[] getFieldNames() {
			return new String[]{"Season", "Episode"};
		}

		public void fetch(HttpApi api, int width, int height) {
		}

		public void paint(Graphics g, int width, int height) {
			if(value[0] != null || value[1] != null) {
				g.setColor(160, 160, 180);
				g.setFont(font);
				int yPos = height-25-2*font.getHeight(); /* bottom - 10px border - 15px progress bar - title label - time label */
				int xPos = (int) (10 + width*FileThumb.maxWidthFactor + 5); /* left + 10px border + maximum file thumb width + 5px space */
				
				if(value[1] != null) {
					g.drawString((String) "Episode: "+value[1], xPos, yPos, Graphics.LEFT | Graphics.BOTTOM);
					yPos -= font.getHeight();
				}
				
				if(value[0] != null) {
					g.drawString((String) "Season: "+value[0], xPos, yPos, Graphics.LEFT | Graphics.BOTTOM);
					yPos -= font.getHeight();
				}
				
			}
		}
	}
	
	protected class ArtistLabel extends StringGUIElement {
		Font artistFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD , Font.SIZE_SMALL);
		Font albumFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN , Font.SIZE_MEDIUM);
		public ArtistLabel() { super(); }
		
		public String[] getFieldNames() {
			return new String[]{"Artist", "Album"};
		}

		public void fetch(HttpApi api, int width, int height) {
		}

		public void paint(Graphics g, int width, int height) {
			if(value[0] != null || value[1] != null) {
				g.setColor(160, 160, 180);
				int yPos = height-25-2*font.getHeight(); /* bottom - 10px border - 15px progress bar - title label - time label */
				int xPos = (int) (10 + width*FileThumb.maxWidthFactor + 5); /* left + 10px border + maximum file thumb width + 5px space */
				
				if(value[1] != null) {
					g.setFont(albumFont);
					g.drawString((String) value[1], xPos, yPos, Graphics.LEFT | Graphics.BOTTOM);
					yPos -= albumFont.getHeight();
				}
				
				if(value[0] != null) {
					g.setFont(artistFont);
					g.drawString((String) value[0], xPos, yPos, Graphics.LEFT | Graphics.BOTTOM);
					yPos -= artistFont.getHeight();
				}
				
			}
		}
	}
	
	protected class TimeLabel extends StringGUIElement {
		public TimeLabel() { super(); }

		public String[] getFieldNames() {
			return new String[] {"Time", "Duration"};
		}

		public void fetch(HttpApi api, int width, int height) {
		}

		public void paint(Graphics g, int width, int height) {
			if(value[0] != null) {
				g.setColor(160, 160, 180);
				g.setFont(font);
				StringBuffer buf = new StringBuffer(value[0]);
				if(value[1] != null) {
					buf.append(" / ");
					buf.append(value[1]);
				}
				g.drawString(buf.toString(), width-10, height-25-font.getHeight(), Graphics.RIGHT | Graphics.BOTTOM);
			}
		}
	}
	
	protected class RemoteControlCanvas extends GameCanvas implements Runnable {
		private boolean dirty = false;
		private boolean shown = false;
		private Vector guiElements;
		
		protected RemoteControlCanvas(String name) {
			super(false);
			setTitle(name);
			guiElements = new Vector();
			guiElements.addElement(new TvshowThumb());
			guiElements.addElement(new FileThumb());
			guiElements.addElement(new PlaybackProgress());
			guiElements.addElement(new TimeLabel());
			guiElements.addElement(new SeasonLabel());
			guiElements.addElement(new ArtistLabel());
			guiElements.addElement(new TitleLabel());
		}
		
		public void run() {
			try {
				synchronized(this) {
					if(shown) {
						if(screenshotMode) {
							drawScreenshot();
						} else {
							drawElements();
						}
					}
				}
			} catch(Exception e) { Logger.getLogger().error(e); }
		}

		private synchronized void drawScreenshot() {
			int height = getHeight();
			int width = getWidth();
			Graphics g = getGraphics();
			g.setColor(0);
			g.fillRect(0, 0, width, height);
			g.drawImage(screenshot, width/2, height/2, Graphics.HCENTER | Graphics.VCENTER);
			flushGraphics();
		}

		private void drawElements() {
			if(dirty) {
				dirty = false;
				int height = getHeight();
				int width = getWidth();
				Graphics g = getGraphics();
				HttpApi api = getApi();
				
				drawBackground(width, height);
				
				try {
					for(Enumeration e = guiElements.elements(); e.hasMoreElements(); ) {
						GUIElement element = (GUIElement) e.nextElement();
						element.fetch(api, width, height);
						element.paint(g, width, height);
						dirty = dirty || element.getDirty();
					}
				} catch(Exception e) {
					Logger.getLogger().error(e);
					e.printStackTrace();
				}

				flushGraphics();
			}
		}
		
		public void refresh() {
			new Thread(this).start();
		}
		
		public synchronized void setValue(String name, String newValue) {
			try {
				for(Enumeration e = guiElements.elements(); e.hasMoreElements(); ) {
					GUIElement element = (GUIElement) e.nextElement();
					dirty = element.updateValue(name, newValue) || dirty;
				}
			} catch(Exception e) {
				Logger.getLogger().error(e);
				e.printStackTrace();
			}
		}
		
		private void drawBackground(int w, int h) {
			Graphics g = getGraphics();
			g.setClip(0, 0, w, h);
			int width = g.getClipWidth();
			int height = g.getClipHeight();
			
			g.setColor(0, 0, 0);
			g.fillRect(0, 0, width, height);
			
			final int iterations = 10;
			for(int i = 0; i<=iterations; i++) {
				g.setColor(i*40/iterations, i*40/iterations, i*40/iterations);
				g.fillRoundRect(i, i, width-2*i, height-2*i, iterations, iterations);
			}
		}
		
		protected void sizeChanged(int w, int h) {
			canvasWidth = getWidth();
			canvasHeight = getHeight();
			Logger.getLogger().info("sizeChanged");
			synchronized(this) {
				try {
					for(Enumeration e = guiElements.elements(); e.hasMoreElements(); ) {
						GUIElement element = (GUIElement) e.nextElement();
						element.sizeChanged(w, h);
					}
				} catch(Exception e) {
					Logger.getLogger().error(e);
					e.printStackTrace();
				}
				dirty = true;
			}
			super.sizeChanged(w, h);
			refresh();
		}
		
		protected void hideNotify() {
			Logger.getLogger().info("hideNotify");
			synchronized(this) {
				shown = false;
			}
			pause();
		}
		
		protected void showNotify() {
			Logger.getLogger().info("showNotify");
			canvasWidth = getWidth();
			canvasHeight = getHeight();
			unpause();
			synchronized(this) {
				shown = true;
			}
			refresh();
		}
		
		private void actOnKey(int keyCode) {
			setTitle(Integer.toString(keyCode));
			if(getGameAction(keyCode) == FIRE) {
				sendKey(KEY_BUTTON_A);
			} else if(getGameAction(keyCode) == UP) {
				sendKey(KEY_BUTTON_DPAD_UP);
			} else if(getGameAction(keyCode) == DOWN) {
				sendKey(KEY_BUTTON_DPAD_DOWN);
			} else if(getGameAction(keyCode) == LEFT) {
				sendKey(KEY_BUTTON_DPAD_LEFT);
			} else if(getGameAction(keyCode) == RIGHT) {
				sendKey(KEY_BUTTON_DPAD_RIGHT);
			} else {
				if( (keyCode >= 'a' && keyCode <= 'z')) {
					/* For ASCII characters the interface wants uppercase,
					 * even though that isn't mentioned in the documentation anywhere
					 */
					sendKey(0xF100 + keyCode - 'a' + 'A');
				} else if( (keyCode > 0 && keyCode <= 127) ) {
					sendKey(0xF000 + keyCode);
				}
			}
		}
		
		protected void keyPressed(int keyCode) {
			//Logger.getLogger().info("Pressed: " + getKeyName(keyCode));
			super.keyPressed(keyCode);
		}
		protected void keyReleased(int keyCode) {
			//Logger.getLogger().info("Released: " + getKeyName(keyCode));
			actOnKey(keyCode);
			super.keyReleased(keyCode);
		}
		protected void keyRepeated(int keyCode) {
			//Logger.getLogger().info("Repeated: " + getKeyName(keyCode));
			actOnKey(keyCode);
			super.keyRepeated(keyCode);
		}
	}
}

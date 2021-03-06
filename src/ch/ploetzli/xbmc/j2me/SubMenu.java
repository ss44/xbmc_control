package ch.ploetzli.xbmc.j2me;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

import ch.ploetzli.xbmc.Logger;

public class SubMenu implements CommandListener {
	protected Display display = null;
	protected SubMenu parent = null;
	protected String name;
	protected SubMenu[] subMenus = null;
	protected Displayable displayable = null;
	protected CommandListener listener = null;
	
	protected static Command backCommand = new Command("Back", Command.BACK, 10);
	
	public SubMenu(String name)
	{
		this.name = name;
	}
	
	public SubMenu(String name, SubMenu[] subMenus)
	{
		this(name);
		this.subMenus = subMenus;
		if(subMenus != null) {
			for(int i = 0; i<subMenus.length; i++) 
				subMenus[i].setParent(this);
		}
	}
	
	public void setParent(SubMenu parent) {
		this.parent = parent;
	}
	
	public SubMenu getParent() {
		return parent;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Return the root of this SubMenu structure, e.g. wander up
	 * the parent chain until there are no more parents.
	 * @return A SubMenu that has a null parent. Can not be null.
	 */
	public SubMenu getRoot() {
		SubMenu result = this;
		while(result.parent != null)
			result = result.parent;
		return result;
	}
	
	/**
	 * Return either this object or one of the recursive 
	 * (depth-first) children that are instances of the
	 * given class and return the first result.
	 * @param cls SubMenu.class or a subclass thereof
	 * @return This object or the first (indirect) child
	 * 	that is an instance of cls. If no child is found
	 * 	return null;
	 */
	public SubMenu getChildByClass(Class cls) {
		if(cls.isInstance(this))
			return this;
		for(int i=0; i<subMenus.length; i++) {
			SubMenu result = subMenus[i].getChildByClass(cls);
			if(result != null)
				return result;
		}
		return null;
	}
	
	/**
	 * Set the Display this menu should be displayed on for
	 * purposes of the show method.
	 * @param display
	 */
	public void setDisplay(Display display) {
		this.display = display;
	}
	
	/**
	 * Get the Display this menu should be display on for
	 * purposes of the show method.
	 * This method call will bubble upwards on the parent chain
	 * if no Display is set for this object, so setDisplay()
	 * generally only needs to be called on the top-level
	 * SubMenu object.
	 * @return
	 */
	public Display getDisplay() {
		if(display != null)
			return display;
		if(parent != null)
			return parent.getDisplay();
		return null;
	}
	
	/**
	 * Get a (possibly cached) Displayable for this menu.
	 * @return A Displayable that represents this menu, either cached
	 * 	or freshly constructed with constructDisplayable
	 */
	public Displayable getDisplayable()
	{
		/* Check for cache */
		if(displayable != null)
			return displayable;
		
		/* Construct new */
		displayable = constructDisplayable();
		return displayable;
	}
	
	/**
	 * Trigger a refresh for this menu.
	 * In SubMenu this method is empty, since the menu is static
	 * but subclasses representing dynamic menus might want to
	 * override this method.
	 */
	public void refresh()
	{
		
	}

	/**
	 * Construct a Displayable for this menu.
	 * Returns a newly constructed Displayable for this menu which will
	 * generally be a sub-menu. The SubMenu class just uses all the
	 * names of its children, but subclasses will want to override
	 * this method to do something appropriate.
	 * @return Newly constructed Displayable
	 */
	protected Displayable constructDisplayable() {
		String labels[];
		if(subMenus != null) {
			labels = new String[subMenus.length];
			for(int i=0; i<subMenus.length; i++)
				labels[i] = subMenus[i].name;
		} else {
			labels = new String[]{"Nothing here"};
		}
		List list = new List(name, List.IMPLICIT, labels, null);
		addPrivateCommands(list);
		list.setCommandListener(this);
		return list;
	}
	
	protected void addPrivateCommands(Displayable d) {
		d.addCommand(backCommand);
	}

	public void setCommandListener(CommandListener listener) {
		this.listener = listener;
	}
	
	public void addCommand(Command cmd) {
		this.getDisplayable().addCommand(cmd);
	}
	
	public void removeCommand(Command cmd) {
		this.getDisplayable().removeCommand(cmd);
	}
	
	public void commandAction(Command cmd, Displayable d) {
		if(cmd == backCommand) {
			if(parent != null)
				show(parent);
		} else if(cmd == List.SELECT_COMMAND) {
			if(displayable != null && displayable instanceof List) {
				int i = ((List)displayable).getSelectedIndex();
				select(i);
			}
		} else if(listener != null) {
			listener.commandAction(cmd, d);
		}
	}

	/**
	 * Called when an item in this menu is selected and our 
	 * 	displayable is a list. The default implementation will
	 * 	show the selected sub menu if available, but subclasses
	 * 	might want to override that to do something else.
	 * @param index The value of getSelectedIndex() as returned
	 * 	by our displayable as a List
	 */
	protected void select(int index) {
		if(subMenus != null) {
			if(index >= 0 && index < subMenus.length)
				showChild(subMenus[index]);
		} else {
			Logger.getLogger().error("No submenus");
		}
	}
	
	/**
	 * Show the given menu on the same Display as the current menu.
	 * Will trigger a refresh of the contents of menu and set it to
	 * be displayed on the same Display as this menu.
	 * @param menu
	 */
	protected void show(SubMenu menu)
	{
		Display d = getDisplay();
		if(d != null) {
			menu.refresh();
			d.setCurrent(menu.getDisplayable());
		} else {
			Logger.getLogger().error("Can't show "+menu.name+" without a Display");
		}
	}
	
	/**
	 * Show the given menu on the same Display as the current menu and
	 * reparent the given menu to be a submenu of this
	 * Will trigger a refresh of the contents of menu and set it to
	 * be displayed on the same Display as this menu.
	 * @param menu
	 */
	protected void showChild(SubMenu menu)
	{
		menu.setParent(this);
		show(menu);
	}
}

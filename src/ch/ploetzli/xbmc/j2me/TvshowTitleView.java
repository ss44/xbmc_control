package ch.ploetzli.xbmc.j2me;

import javax.microedition.lcdui.Image;

public class TvshowTitleView extends DatabaseView {
	public static DatabaseView get(String name) {
		return get(name, null);
	}
	
	public static DatabaseView get(String name, String whereClause) {
		return get(name, "tvshowview", whereClause);
	}
	
	public static DatabaseView get(String name, String table, String whereClause) {
		return get(TvshowTitleView.class, name, "tvshowview.idShow", new String[]{"c00", "tvshowview.totalcount", "tvshowview.watchedcount"}, table, "c00", null, whereClause);
	}
	
	protected Object[] formatRow(int index, String[] data)
	{
		String label = "No label";
		Image img = null;
		if(data.length > 3) {
			label = data[1];
			try {
				int total = Integer.parseInt(data[2]);
				int watched = Integer.parseInt(data[3]);
				if(watched == 0)
					img = ImageFactory.getResourceImage(ImageFactory.ICON_EMPTY);
				else if(watched < total)
					img = ImageFactory.getResourceImage(ImageFactory.ICON_HALF);
				else if(watched == total)
					img = ImageFactory.getResourceImage(ImageFactory.ICON_FULL);
			} catch(Exception e) {
				/* Ignore, but set no image */
				e.printStackTrace();
			}
			if(keyColumn != null && data.length > 1) {
				label = data[1];
			}
		}
		return new Object[]{label, img};
	}
}

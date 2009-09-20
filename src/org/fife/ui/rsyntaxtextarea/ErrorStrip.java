/*
 * 08/10/2009
 *
 * ErrorStrip.java - A component that can visually show Parser messages (syntax
 * errors, etc.) in an RSyntaxTextArea.
 * Copyright (C) 2009 Robert Futrell
 * robert_futrell at users.sourceforge.net
 * http://fifesoft.com/rsyntaxtextarea
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA.
 */
package org.fife.ui.rsyntaxtextarea;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.parser.Parser;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;


/**
 * A component to sit alongside an {@link RSyntaxTextArea} that displays
 * colored markers for locations of interest (parser errors, marked
 * occurrences, etc.).<p>
 *
 * An <code>ErrorStrip</code> can be added to a UI like so:
 * <pre>
 * textArea = createTextArea();
 * scrollPane = new RTextScrollPane(textArea, true);
 * ErrorStrip es = new ErrorStrip(textArea);
 * JPanel temp = new JPanel(new BorderLayout());
 * temp.add(scrollPane);
 * temp.add(es, BorderLayout.LINE_END);
 * </pre>
 *
 * @author Robert Futrell
 * @version 0.1
 */
/*
 * Possible improvements:
 *    1. Handle marked occurrence changes separately from parser changes.
 *       For each property change, call a method that removes the notices
 *       being reloaded from the Markers (removing any Markers that are now
 *       "empty").
 *    2. When 1.4 support is dropped, replace new Integer(int) with
 *       Integer.valueOf(int).
 */
public class ErrorStrip extends JComponent {

	/**
	 * The text area.
	 */
	private RSyntaxTextArea textArea;

	/**
	 * Listens for events in this component.
	 */
	private Listener listener;

	/**
	 * Whether "marked occurrences" in the text area should be shown in this
	 * error strip.
	 */
	private boolean showMarkedOccurrences;

	/**
	 * Mapping of colors to brighter colors.  This is kept to prevent
	 * unnecessary creation of the same Colors over and over.
	 */
	private Map brighterColors;

	/**
	 * The preferred width of this component.
	 */
	private static final int PREFERRED_WIDTH = 14;

	private static final String MSG = "org.fife.ui.rsyntaxtextarea.ErrorStrip";
	private static final ResourceBundle msg = ResourceBundle.getBundle(MSG);


	/**
	 * Constructor.
	 *
	 * @param textArea The text area we are examining.
	 */
	public ErrorStrip(RSyntaxTextArea textArea) {
		this.textArea = textArea;
		listener = new Listener();
		textArea.addPropertyChangeListener(
				RSyntaxTextArea.PARSER_NOTICES_PROPERTY, listener);
		textArea.addPropertyChangeListener(
				RSyntaxTextArea.MARK_OCCURRENCES_PROPERTY, listener);
		textArea.addPropertyChangeListener(
				RSyntaxTextArea.MARKED_OCCURRENCES_CHANGED_PROPERTY, listener);
		ToolTipManager.sharedInstance().registerComponent(this);
		setLayout(null); // Manually layout Markers as they can overlap
		addMouseListener(listener);
		setShowMarkedOccurrences(true);
	}


	/**
	 * Manually manages layout since this component uses no layout manager.
	 */
	public void doLayout() {
		for (int i=0; i<getComponentCount(); i++) {
			Marker m = (Marker)getComponent(i);
			m.updateLocation();
		}
	}


	/**
	 * Returns a "brighter" color.
	 *
	 * @param c The color.
	 * @return A brighter color.
	 */
	private Color getBrighterColor(Color c) {
		if (brighterColors==null) {
			brighterColors = new HashMap(5); // Usually small
		}
		Color brighter = (Color)brighterColors.get(c);
		if (brighter==null) {
			// Don't use c.brighter() as it doesn't work well for blue, and
			// also doesn't return something brighter "enough."
			int r = possiblyBrighter(c.getRed());
			int g = possiblyBrighter(c.getGreen());
			int b = possiblyBrighter(c.getBlue());
			brighter = new Color(r, g, b);
			brighterColors.put(c, brighter);
		}
		return brighter;
	}


	/**
	 * {@inheritDoc}
	 */
	public Dimension getPreferredSize() {
		int height = textArea.getPreferredScrollableViewportSize().height;
		return new Dimension(PREFERRED_WIDTH, height);
	}


	/**
	 * Returns whether marked occurrences are shown in this error strip.
	 *
	 * @return Whether marked occurrences are shown.
	 * @see #setShowMarkedOccurrences(boolean)
	 */
	public boolean getShowMarkedOccurrences() {
		return showMarkedOccurrences;
	}


	/**
	 * {@inheritDoc}
	 */
	public String getToolTipText(MouseEvent e) {
		String text = null;
		int line = yToLine(e.getY());
		if (line>-1) {
			text = msg.getString("Line");
			// 1.5: Use Integer.valueOf(line)
			text = MessageFormat.format(text,
					new Object[] { new Integer(line) });
		}
		return text;
	}


	/**
	 * Returns the y-offset in this component corresponding to a line in the
	 * text component.
	 *
	 * @param line The line.
	 * @return The y-offset.
	 * @see #yToLine(int)
	 */
	private int lineToY(int line) {
		int h = textArea.getVisibleRect().height;
		float lineCount = textArea.getLineCount();
		return (int)((line/lineCount) * h) - 2;		
	}


	/**
	 * Returns a possibly brighter component for a color.
	 *
	 * @param i An RGB component for a color (0-255).
	 * @return A possibly brighter value for the component.
	 */
	private static final int possiblyBrighter(int i) {
		if (i<255) {
			i += (int)((255-i)*0.8f);
		}
		return i;
	}


	/**
	 * Refreshes the markers displayed in this error strip.
	 */
	private void refreshMarkers() {

		removeAll();
		Map markerMap = new HashMap();

		List notices = textArea.getParserNotices();
		for (Iterator i=notices.iterator(); i.hasNext(); ) {
			ParserNotice notice = (ParserNotice)i.next();
			// 1.5: Use Integer.valueOf(notice.getLine())
			Integer key = new Integer(notice.getLine());
			Marker m = (Marker)markerMap.get(key);
			if (m==null) {
				m = new Marker(notice);
				m.addMouseListener(listener);
				markerMap.put(key, m);
				add(m);
			}
			else {
				m.addNotice(notice);
			}
		}

		if (getShowMarkedOccurrences() && textArea.getMarkOccurrences()) {
			List occurrences = textArea.getMarkedOccurrences();
			for (Iterator i=occurrences.iterator(); i.hasNext(); ) {
				DocumentRange range = (DocumentRange)i.next();
				int line = 0;
				try {
					line = textArea.getLineOfOffset(range.getStartOffset());
				} catch (BadLocationException ble) { // Never happens
					continue;
				}
				ParserNotice notice = new MarkedOccurrenceNotice(range);
				// 1.5: Use Integer.valueOf(notice.getLine())
				Integer key = new Integer(line);
				Marker m = (Marker)markerMap.get(key);
				if (m==null) {
					m = new Marker(notice);
					m.addMouseListener(listener);
					markerMap.put(key, m);
					add(m);
				}
				else {
					if (!m.containsMarkedOccurence()) {
						m.addNotice(notice);
					}
				}
			}
		}

		revalidate();
		repaint();

	}


	/**
	 * {@inheritDoc}
	 */
	public void removeNotify() {
		super.removeNotify();
		textArea.removePropertyChangeListener(
				RSyntaxTextArea.PARSER_NOTICES_PROPERTY, listener);
		textArea.removePropertyChangeListener(
				RSyntaxTextArea.MARK_OCCURRENCES_PROPERTY, listener);
		textArea.removePropertyChangeListener(
				RSyntaxTextArea.MARKED_OCCURRENCES_CHANGED_PROPERTY, listener);
	}


	/**
	 * Sets whether marked occurrences are shown in this error strip.
	 *
	 * @param show Whether to show marked occurrences.
	 * @see #getShowMarkedOccurrences()
	 */
	public void setShowMarkedOccurrences(boolean show) {
		if (show!=showMarkedOccurrences) {
			showMarkedOccurrences = show;
			if (isDisplayable()) { // Skip this when we're first created
				refreshMarkers();
			}
		}
	}


	/**
	 * Returns the line in the text area corresponding to a y-offset in this
	 * component.
	 *
	 * @param y The y-offset.
	 * @return The line.
	 * @see #lineToY(int)
	 */
	private final int yToLine(int y) {
		int line = -1;
		int h = textArea.getVisibleRect().height;
		if (y<h) {
			float at = y/(float)h;
			line = (int)(textArea.getLineCount()*at);
		}
		return line;
	}


	/**
	 * Listens for events in the error strip and its markers.
	 */
	private class Listener extends MouseAdapter
							implements PropertyChangeListener {

		public void mouseClicked(MouseEvent e) {

			Component source = (Component)e.getSource();
			if (source instanceof Marker) {
				((Marker)source).mouseClicked(e);
				return;
			}

			int line = yToLine(e.getY());
			if (line>-1) {
				try {
					int offs = textArea.getLineStartOffset(line);
					textArea.setCaretPosition(offs);
				} catch (BadLocationException ble) { // Never happens
					UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				}
			}

		}

		public void propertyChange(PropertyChangeEvent e) {

			String propName = e.getPropertyName();

			// If they change whether marked occurrences are visible in editor
			if (RSyntaxTextArea.MARK_OCCURRENCES_PROPERTY.equals(propName)) {
				if (getShowMarkedOccurrences()) {
					refreshMarkers();
				}
			}

			// If parser notices changed.
			else if (RSyntaxTextArea.PARSER_NOTICES_PROPERTY.equals(propName)) {
				refreshMarkers();
			}

			// If marked occurrences changed.
			else if (RSyntaxTextArea.MARKED_OCCURRENCES_CHANGED_PROPERTY.
					equals(propName)) {
				if (getShowMarkedOccurrences()) {
					refreshMarkers();
				}
			}

		}

	}


private static final Color COLOR = new Color(220, 220, 220);
	/**
	 * A notice that wraps a "marked occurrence."
	 */
	private class MarkedOccurrenceNotice implements ParserNotice {

		private DocumentRange range;

		public MarkedOccurrenceNotice(DocumentRange range) {
			this.range = range;
		}

		public int compareTo(Object o) {
			return 0; // Value doesn't matter
		}

		public boolean containsPosition(int pos) {
			return pos>=range.getStartOffset() && pos<range.getEndOffset();
		}

		public boolean equals(Object o) {
			// FindBugs - Define equals() when defining compareTo()
			return compareTo(o)==0;
		}

		public Color getColor() {
			return COLOR;
			//return textArea.getMarkOccurrencesColor();
		}

		public int getLength() {
			return range.getEndOffset() - range.getStartOffset();
		}

		public int getLevel() {
			return INFO; // Won't matter
		}

		public int getLine() {
			try {
				return textArea.getLineOfOffset(range.getStartOffset());
			} catch (BadLocationException ble) {
				return 0;
			}
		}

		public String getMessage() {
			String text = null;
			try {
				String word = textArea.getText(range.getStartOffset(),
												getLength());
				text = msg.getString("OccurrenceOf");
				text = MessageFormat.format(text, new String[] { word });
			} catch (BadLocationException ble) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
			}
			return text;
		}

		public int getOffset() {
			return range.getStartOffset();
		}

		public Parser getParser() {
			return null;
		}

		public boolean getShowInEditor() {
			return false; // Value doesn't matter
		}

		public String getToolTipText() {
			return null;
		}

		public int hashCode() { // FindBugs, since we override equals()
			return 0; // Value doesn't matter for us.
		}

	}


	/**
	 * A "marker" in this error strip, representing one or more notices.
	 */
	private class Marker extends JComponent {

		private List notices;

		public Marker(ParserNotice notice) {
			notices = new ArrayList(1); // Usually just 1
			addNotice(notice);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setSize(getPreferredSize());
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		public void addNotice(ParserNotice notice) {
			notices.add(notice);
		}

		public boolean containsMarkedOccurence() {
			boolean result = false;
			for (int i=0; i<notices.size(); i++) {
				if (notices.get(i) instanceof MarkedOccurrenceNotice) {
					result = true;
					break;
				}
			}
			return result;
		}

		public Color getColor() {
			// Return the color for the hightest-level parser.
			Color c = null;
			int lowestLevel = Integer.MAX_VALUE; // ERROR is 0
			for (Iterator i=notices.iterator(); i.hasNext(); ) {
				ParserNotice notice = (ParserNotice)i.next();
				if (notice.getLevel()<lowestLevel) {
					lowestLevel = notice.getLevel();
					c = notice.getColor();
				}
			}
			return c;
		}

		public Dimension getPreferredSize() {
			int w = PREFERRED_WIDTH - 4; // 2-pixel empty border
			return new Dimension(w, 5);
		}

		public String getToolTipText() {

			String text = null;

			if (notices.size()==1) {
				text = ((ParserNotice)notices.get(0)).getMessage();
			}
			else { // > 1
				StringBuffer sb = new StringBuffer("<html>");
				sb.append(msg.getString("MultipleMarkers"));
				sb.append("<br>");
				for (int i=0; i<notices.size(); i++) {
					ParserNotice pn = (ParserNotice)notices.get(i);
					sb.append("&nbsp;&nbsp;&nbsp;- ");
					sb.append(pn.getMessage());
					sb.append("<br>");
				}
				text = sb.toString();
			}

			return text;

		}

		protected void mouseClicked(MouseEvent e) {
			ParserNotice pn = (ParserNotice)notices.get(0);
			int offs = pn.getOffset();
			int len = pn.getLength();
			if (offs>-1 && len>-1) { // These values are optional
				textArea.setSelectionStart(offs);
				textArea.setSelectionEnd(offs+len);
			}
			else {
				int line = pn.getLine();
				try {
					offs = textArea.getLineStartOffset(line);
					textArea.setCaretPosition(offs);
				} catch (BadLocationException ble) { // Never happens
					UIManager.getLookAndFeel().provideErrorFeedback(textArea);
				}
			}
		}

		protected void paintComponent(Graphics g) {

			// TODO: Give "priorities" and always pick color of a notice with
			// highest priority (e.g. parsing errors will usually be red).

			Color borderColor = getColor();
			if (borderColor==null) {
				borderColor = Color.DARK_GRAY;
			}
			Color fillColor = getBrighterColor(borderColor);

			int w = getWidth();
			int h = getHeight();

			g.setColor(fillColor);
			g.fillRect(0,0, w,h);

			g.setColor(borderColor);
			g.drawRect(0,0, w-1,h-1);

		}

		public void removeNotify() {
			super.removeNotify();
			ToolTipManager.sharedInstance().unregisterComponent(this);
			removeMouseListener(listener);
		}

		public void updateLocation() {
			int line = ((ParserNotice)notices.get(0)).getLine();
			int y = lineToY(line);
			setLocation(2, y);
		}

	}


}
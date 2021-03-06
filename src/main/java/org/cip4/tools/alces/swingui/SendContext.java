package org.cip4.tools.alces.swingui;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import org.apache.log4j.Logger;
import org.cip4.tools.alces.util.ConfigurationHandler;
import org.cip4.tools.alces.util.JDFFileFilter;
import org.cip4.tools.alces.util.JMFFileFilter;

/**
 * Represents the context menu for the Send JMF-File Button
 * 
 * @author Marco Kornrumpf (Marco.Kornrumpf@Bertelsmann.de)
 * 
 */
public class SendContext extends JPopupMenu implements MouseListener {

	private static Logger LOGGER = Logger.getLogger(SendContext.class);

	private JMenuItem label = null;

	private JMenuItem fileItem = null;

	private JSeparator sep = null;

	private Alces _source = null;

	private String _dir = null;

	private ArrayList<String> _entries;

	private ConfigurationHandler _confHand = null;

	/**
	 * Simple constructor <code>source</code> is used to call the JOptionPane. <code>type</code> describes if the context is called for send JMF or
	 * SubmitQueueEntry
	 * 
	 * @param source
	 * @param type
	 */
	public SendContext(Alces source, String type) {
		super();
		_confHand = ConfigurationHandler.getInstance();
		_source = source;

		label = new JMenuItem(_confHand.getLabel("Select file to send:", "Select file to send:"));
		this.add(label);
		sep = new JSeparator();
		this.add(sep);
		this.setSize(200, 100);
		if (type.equals("jmf")) {
			createJMFItems();
		}
		if (type.equals("jdf"))
			createJDFItems();
		this.setVisible(true);
	}

	/**
	 * Creates all dynamic menuItems for the popupMenu
	 * 
	 */
	private void createJMFItems() {

		_dir = _confHand.getProp(ConfigurationHandler.LAST_DIR);
		_entries = new ArrayList<String>();
		String[] testFile = new File(_dir).list();

		for (int i = 0; i < testFile.length; i++) {
			if (new JMFFileFilter().acceptFilesOnly(new File(_dir + "" + File.separator + "" + testFile[i])))

			{
				_entries.add(testFile[i]);

			}
		}
		try {
			for (int i = 0; i < _entries.size(); i++) {
				this.add(fileItem = new JMenuItem(_entries.get(i).toString()));
				fileItem.setName(_entries.get(i).toString());
				fileItem.addMouseListener(this);
				try {
					fileItem.setToolTipText((new File(_dir)).getCanonicalPath() + "" + File.separator + "" + _entries.get(i).toString());
				} catch (IOException e) {

					LOGGER.error(e);
				}

			}
		} catch (NullPointerException e) {
			JOptionPane.showMessageDialog(_source, _confHand.getLabel("Error Directory", "No directory found! Please set a default directory in alces.properties."), "Test",
					JOptionPane.WARNING_MESSAGE);
			label.setText(_confHand.getLabel("Error", "Error"));

		}

	}

	/**
	 * Creates all dynamic menuItems for the popupMenu
	 * 
	 */
	private void createJDFItems() {

		_dir = _confHand.getProp(ConfigurationHandler.LAST_DIR);
		_entries = new ArrayList<String>();
		String[] testFile = new File(_dir).list();

		for (int i = 0; i < testFile.length; i++) {
			if (new JDFFileFilter().acceptFilesOnly(new File(_dir + "" + File.separator + "" + testFile[i])))

			{
				_entries.add(testFile[i]);

			}
		}

		try {
			for (int i = 0; i < _entries.size(); i++) {
				this.add(fileItem = new JMenuItem(_entries.get(i).toString()));
				fileItem.setName(_entries.get(i).toString());
				fileItem.addMouseListener(this);
				try {
					fileItem.setToolTipText((new File(_dir)).getCanonicalPath() + "" + File.separator + "" + _entries.get(i).toString());
				} catch (IOException e) {

					LOGGER.error(e);
				}

			}
		} catch (NullPointerException e) {
			JOptionPane.showMessageDialog(_source, _confHand.getLabel("Error Directory", "No directory found! Please set a default directory in alces.properties."), "Test",
					JOptionPane.WARNING_MESSAGE);
			label.setText(_confHand.getLabel("Error", "Error"));

		}

	}

	public void mouseClicked(MouseEvent e) {
		// Not implemented
	}

	public void mouseEntered(MouseEvent e) {
		// Not implemented
	}

	public void mouseExited(MouseEvent e) {
		// Not implemented
	}

	public void mousePressed(MouseEvent e) {
		Component comp = null;
		comp = e.getComponent();
		if (comp.getName().endsWith(".jmf")) {
			_source.loadContextMessage(new File(_dir + "" + File.separator + "" + comp.getName()));
		}
		if (comp.getName().endsWith(".jdf")) {
			_source.loadContextJDF(new File(_dir + "" + File.separator + "" + comp.getName()));
		}
	}

	public void mouseReleased(MouseEvent e) {
		// Not implemented
	}

}

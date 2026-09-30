/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.debug.gui.decompiler;

import java.awt.*;
import java.math.BigInteger;
import java.util.List;

import javax.swing.JPanel;

import docking.widgets.fieldpanel.LayoutModel;
import docking.widgets.fieldpanel.listener.IndexMapper;
import docking.widgets.fieldpanel.listener.LayoutModelListener;
import ghidra.app.decompiler.ClangLine;
import ghidra.app.decompiler.DecompilerMarginService;
import ghidra.app.decompiler.component.margin.DecompilerMarginProvider;
import ghidra.app.decompiler.component.margin.LayoutPixelIndexMap;
import ghidra.app.plugin.core.debug.gui.DebuggerResources;
import ghidra.program.model.listing.Program;

/**
 * A Decompiler margin that draws the tracked-register marker beside each line holding part of the
 * instruction at the program counter
 */
public class PcDecompilerMarginProvider extends JPanel
		implements DecompilerMarginProvider, LayoutModelListener {

	private final DebuggerDecompilerPcMarkerPlugin plugin;

	private Program program;
	private LayoutModel model;
	private LayoutPixelIndexMap pixmap;

	public PcDecompilerMarginProvider(DebuggerDecompilerPcMarkerPlugin plugin) {
		this.plugin = plugin;
		setPreferredSize(new Dimension(16, 0));
	}

	@Override
	public void setProgram(Program program, LayoutModel model, LayoutPixelIndexMap pixmap) {
		this.program = program;
		setLayoutManager(model);
		this.pixmap = pixmap;
		repaint();
	}

	private void setLayoutManager(LayoutModel model) {
		if (this.model == model) {
			return;
		}
		if (this.model != null) {
			this.model.removeLayoutModelListener(this);
		}
		this.model = model;
		if (this.model != null) {
			this.model.addLayoutModelListener(this);
		}
	}

	@Override
	public Component getComponent() {
		return this;
	}

	@Override
	public void modelSizeChanged(IndexMapper indexMapper) {
		repaint();
	}

	@Override
	public void dataChanged(BigInteger start, BigInteger end) {
		repaint();
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		DecompilerMarginService marginService = plugin.getMarginService();
		if (marginService == null || pixmap == null || program == null) {
			return;
		}
		Rectangle visible = getVisibleRect();
		BigInteger startIdx = pixmap.getIndex(visible.y);
		BigInteger endIdx = pixmap.getIndex(visible.y + visible.height);

		List<ClangLine> lines = marginService.getDecompilerPanel().getLines();
		for (BigInteger index = startIdx; index.compareTo(endIdx) <= 0; index =
			index.add(BigInteger.ONE)) {
			int i = index.intValue();
			if (i >= lines.size()) {
				continue;
			}
			if (plugin.isPcLine(program, lines.get(i))) {
				DebuggerResources.ICON_REGISTER_MARKER.paintIcon(this, g, 0,
					pixmap.getPixel(index));
			}
		}
	}
}

package dev.shafthelper.client;  
  
import java.util.ArrayList;  
import java.util.List;  
  
import dev.shafthelper.ui.GuideText;  
import net.minecraft.client.gui.GuiGraphicsExtractor;  
import net.minecraft.client.gui.components.Button;  
import net.minecraft.client.gui.screens.Screen;  
import net.minecraft.network.chat.Component;  
import net.minecraft.util.FormattedCharSequence;  
  
/**  
 * Renders the /shaft guide (the four GuideText pages that used to be dumped to  
 * chat) as a proper scrollable screen. Page tabs switch which GuideText.page(n)  
 * is shown; the colored/bold Component formatting is preserved via font.split.  
 */  
public final class ShaftGuideScreen extends Screen {  
  
    private static final int PANEL_PAD = 16;  
    private static final int HEADER_TOP = 14;  
    private static final int FIELD_HEIGHT = 18;  
    private static final int LINE_GAP = 3;  
  
    private static final int LIST_BG = 0xD0152535;  
    private static final int LIST_BORDER = 0xFF2A3B55;  
  
    private int panelLeft, panelTop, panelRight, panelBottom, titleY;  
    private int listX, listWidth, viewportTop, viewportBottom;  
  
    private int page;  
    private int scrollOffset = 0;  
    private int contentHeight = 0;  
  
    // Wrapped, formatting-preserving lines for the current page.  
    private final List<FormattedCharSequence> wrapped = new ArrayList<>();  
  
    public ShaftGuideScreen() {  
        this(1);  
    }  
  
    public ShaftGuideScreen(int page) {  
        super(Component.literal("Mineshaft Calculator Guide"));  
        this.page = Math.min(Math.max(page, 1), GuideText.PAGE_TITLES.size());  
    }  
  
    @Override  
    protected void init() {  
        panelLeft = PANEL_PAD;  
        panelRight = this.width - PANEL_PAD;  
        panelTop = HEADER_TOP;  
        panelBottom = this.height - PANEL_PAD;  
        titleY = PANEL_PAD;  
  
        listX = panelLeft + 8;  
        listWidth = (panelRight - panelLeft) - 16;  
        viewportTop = panelTop + 64;          // room for title + page tabs  
        viewportBottom = panelBottom - 8;  
  
        addPageTabs();  
        addBottomButtons();  
        wrapCurrentPage();  
    }  
  
    private void addPageTabs() {  
        int count = GuideText.PAGE_TITLES.size();  
        int gap = 6;  
        int bw = 30;  
        int totalW = bw * count + gap * (count - 1);  
        int startX = panelLeft + (panelRight - panelLeft - totalW) / 2;  
        int y = panelTop + 32;  
        for (int i = 1; i <= count; i++) {  
            final int target = i;  
            Button b = Button.builder(Component.literal(String.valueOf(i)), btn -> {  
                page = target;  
                scrollOffset = 0;  
                rebuild();  
            }).bounds(startX + (i - 1) * (bw + gap), y, bw, FIELD_HEIGHT).build();  
            b.active = (i != page); // current page tab is disabled  
            addRenderableWidget(b);  
        }  
    }  
  
    private void addBottomButtons() {  
        int bw = 90;  
        int y = panelBottom + 6;  
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())  
            .bounds((this.width - bw) / 2, y, bw, FIELD_HEIGHT).build());  
    }  
  
    private void wrapCurrentPage() {  
        wrapped.clear();  
        int maxWidth = listWidth - 12;  
        for (Component line : GuideText.page(page)) {  
            List<FormattedCharSequence> parts = this.font.split(line, maxWidth);  
            if (parts.isEmpty()) {  
                wrapped.add(FormattedCharSequence.EMPTY); // preserve blank spacing  
            } else {  
                wrapped.addAll(parts);  
            }  
        }  
        contentHeight = wrapped.size() * (this.font.lineHeight + LINE_GAP);  
    }  
  
    @Override  
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {  
        if (mouseX >= listX && mouseX <= listX + listWidth  
            && mouseY >= viewportTop && mouseY <= viewportBottom) {  
            int step = (int) (-scrollY * (this.font.lineHeight + LINE_GAP) * 3);  
            int maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));  
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + step));  
            return true;  
        }  
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);  
    }  
  
    private void rebuild() {  
        this.clearWidgets();  
        this.init();  
    }  
  
    @Override  
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {  
        int bg = ShaftTracker.config().themeBg;
        int border = ShaftTracker.config().themeBorder;
        // Panel background + border  
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, bg);  
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, border);  
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, border);  
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, border);  
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, border);  
  
        // Text viewport background  
        graphics.fill(listX, viewportTop, listX + listWidth, viewportBottom, LIST_BG);  
        graphics.fill(listX, viewportTop, listX + listWidth, viewportTop + 1, LIST_BORDER);  
        graphics.fill(listX, viewportBottom - 1, listX + listWidth, viewportBottom, LIST_BORDER);  
  
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);  
  
        int labelColor = ShaftTracker.config().themeText;
        // Title  
        String title = this.title.getString();  
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, titleY, labelColor, true);  
  
        // Guide text lines, scrolled and clipped to the viewport  
        int lineStep = this.font.lineHeight + LINE_GAP;  
        int y = viewportTop + 6 - scrollOffset;  
        for (FormattedCharSequence line : wrapped) {  
            if (y + lineStep > viewportTop && y < viewportBottom) {  
                graphics.text(this.font, line, listX + 6, y, labelColor, true);  
            }  
            y += lineStep;  
        }  
    }  
}
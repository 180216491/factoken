#include "titlebar.h"
#include "guiutil.h"
#include <QHBoxLayout>
#include <QPainter>
#include <QFile>
#include <QMouseEvent>
#include <QMessageBox>

#define BUTTON_HEIGHT   30
#define BUTTON_WIDTH    40
#define TITLE_HEIGHT    70

#define LEFT_MARGIN     10
#define TOP_MARGIN      0
#define RIGHT_MARGIN    0
#define BOTTOM_MARGIN   1

TitleBar::TitleBar(QWidget *parent)
    : QWidget(parent)
    , fatherWidget(parent)
    , red(255)
    , green(255)
    , bule(255)
    , fPressed(false)
    , buttonType(MIN_BUTTON)
    , borderWidth()
{
    initControl();
    initConnections();
    setQSS();
}

TitleBar::~TitleBar()
{
}

void TitleBar::initControl()
{
    // 1. icon
    iconLabel = new QLabel;

    // 2. content
    contentLabel = new QLabel;
    contentLabel->setObjectName("TitleContent");

    QFont font;
    font.setPixelSize(gBaseFontSize + 3);
    contentLabel->setFont(font);

    // 3. menu button
    fileButton = new QPushButton(tr("&File"));
    settingsButton = new QPushButton(tr("&Settings"));
    helpButton = new QPushButton(tr("&Help"));

    fileButton->setFixedSize(QSize(80, 30));
    settingsButton->setFixedSize(QSize(80, 30));
    helpButton->setFixedSize(QSize(80, 30));

    font.setPixelSize(gBaseFontSize + 2);
    fileButton->setFont(font);
    settingsButton->setFont(font);
    helpButton->setFont(font);

    // 4. min/max/restore/close button
    minButton = new QPushButton;
    restoreButton = new QPushButton;
    maxButton = new QPushButton;
    closeButton = new QPushButton;

    minButton->setFixedSize(QSize(BUTTON_WIDTH, BUTTON_HEIGHT));
    restoreButton->setFixedSize(QSize(BUTTON_WIDTH, BUTTON_HEIGHT));
    maxButton->setFixedSize(QSize(BUTTON_WIDTH, BUTTON_HEIGHT));
    closeButton->setFixedSize(QSize(BUTTON_WIDTH, BUTTON_HEIGHT));

    minButton->setObjectName("MinButton");
    restoreButton->setObjectName("RestoreButton");
    maxButton->setObjectName("MaxButton");
    closeButton->setObjectName("CloseButton");

    minButton->setToolTip(tr("Minimize"));
    restoreButton->setToolTip(tr("Restore"));
    maxButton->setToolTip(tr("Maximize"));
    closeButton->setToolTip(tr("Close and exit"));
	
	//logi.2018.6.24   -  set only min window
	setButtonType(MIN_BUTTON);

    // 5. layout
    QHBoxLayout* mainLayout = new QHBoxLayout(this);
    mainLayout->addWidget(iconLabel);
    mainLayout->addWidget(contentLabel);
    mainLayout->addWidget(fileButton);
    mainLayout->addWidget(settingsButton);
    mainLayout->addWidget(helpButton);
    mainLayout->addWidget(minButton);
    mainLayout->addWidget(restoreButton);
    mainLayout->addWidget(maxButton);
    mainLayout->addWidget(closeButton);
    mainLayout->setContentsMargins(LEFT_MARGIN, TOP_MARGIN, RIGHT_MARGIN, BOTTOM_MARGIN);

    contentLabel->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    this->setFixedHeight(TITLE_HEIGHT);
    this->setWindowFlags(Qt::FramelessWindowHint);
}

void TitleBar::initConnections()
{
    connect(minButton, SIGNAL(clicked()), this, SLOT(onMinButtonClicked()));
    connect(restoreButton, SIGNAL(clicked()), this, SLOT(onRestoreButtonClicked()));
    connect(maxButton, SIGNAL(clicked()), this, SLOT(onMaxButtonClicked()));
    connect(closeButton, SIGNAL(clicked()), this, SLOT(onCloseButtonClicked()));
}

void TitleBar::setQSS()
{
    setStyleSheet("QWidget{ background: rgb(255, 255, 255); }"
                  "QPushButton::menu-indicator{ image: None; }"
                  "QPushButton{ border: 0px; padding: 0px; }"
                  "QPushButton:hover{ background: rgb(202, 232, 255); }"
                  "QPushButton:pressed{ background: rgb(145, 201, 247); }"
                  "QPushButton#MinButton{ border-image:url(:/icons/min) 0 120 0 0; }"
                  "QPushButton#MinButton:hover{ border-image:url(:/icons/min) 0 80 0 40; }"
                  "QPushButton#MinButton:pressed{ border-image:url(:/icons/min) 0 40 0 80; }"
                  "QPushButton#MaxButton{ border-image:url(:/icons/max) 0 120 0 0; }"
                  "QPushButton#MaxButton:hover{ border-image:url(:/icons/max) 0 80 0 40; }"
                  "QPushButton#MaxButton:pressed{ border-image:url(:/icons/max) 0 40 0 80; }"
                  "QPushButton#RestoreButton{ border-image:url(:/icons/restore) 0 120 0 0; }"
                  "QPushButton#RestoreButton:hover{ border-image:url(:/icons/restore) 0 80 0 40; }"
                  "QPushButton#RestoreButton:pressed{ border-image:url(:/icons/restore) 0 40 0 80; }"
                  "QPushButton#CloseButton{ border-image:url(:/icons/close) 0 120 0 0; }"
                  "QPushButton#CloseButton:hover{ border-image:url(:/icons/close) 0 80 0 40; }"
                  "QPushButton#CloseButton:pressed{ border-image:url(:/icons/close) 0 40 0 80 ;}"
                 );
}

void TitleBar::createFileMenu(QMenu* menu)
{
    fileButton->setMenu(menu);
}

void TitleBar::createSettingsMenu(QMenu* menu)
{
    settingsButton->setMenu(menu);
}

void TitleBar::createHelpMenu(QMenu* menu)
{
    helpButton->setMenu(menu);
}

void TitleBar::setBackgroundColor(int r, int g, int b)
{
    red = r;
    green = g;
    bule = b;
    update();
}

void TitleBar::setIcon(QString iconPath, QSize iconSize)
{
    QPixmap icon(iconPath);
    iconLabel->setPixmap(icon.scaled(iconSize));
}

void TitleBar::setContent(QString content)
{
    contentLabel->setText(content);
    this->content = content;
}

void TitleBar::setWidth(int width)
{
    this->setFixedWidth(width);
}

void TitleBar::setButtonType(ButtonType buttonType)
{
    this->buttonType = buttonType;

    switch (buttonType)
    {
    case MIN_BUTTON:
        {
            restoreButton->setVisible(false);
            maxButton->setVisible(false);
        }
        break;
    case MIN_MAX_BUTTON:
        {
            restoreButton->setVisible(false);
        }
        break;
    case ONLY_CLOSE_BUTTON:
        {
            minButton->setVisible(false);
            restoreButton->setVisible(false);
            maxButton->setVisible(false);
        }
        break;
    default:
        break;
    }
}

void TitleBar::setBorderWidth(int borderWidth)
{
    this->borderWidth = borderWidth;
}

void TitleBar::saveRestoreInfo(const QPoint point, const QSize size)
{
    restorePos = point;
    restoreSize = size;
}

void TitleBar::getRestoreInfo(QPoint& point, QSize& size)
{
    point = restorePos;
    size = restoreSize;
}

void TitleBar::paintEvent(QPaintEvent *event)
{
    QPainter painter(this);
    QPainterPath pathBack;
    pathBack.setFillRule(Qt::WindingFill);
    pathBack.addRoundedRect(QRect(0, 0, this->width(), this->height()), 3, 3);
    painter.setRenderHint(QPainter::SmoothPixmapTransform, true);
    painter.fillPath(pathBack, QBrush(QColor(red, green, bule)));

    QPoint lineStartPos, lineEndPos;
    int height = this->height();

    lineStartPos.setX(0);
    lineStartPos.setY(height - BOTTOM_MARGIN);
    lineEndPos.setX(gToolBarWidth - 1);
    lineEndPos.setY(height - BOTTOM_MARGIN);
    painter.setPen(QColor(52, 73, 94));
    painter.drawLine(lineStartPos, lineEndPos);

    lineStartPos.setX(gToolBarWidth);
    lineStartPos.setY(height - BOTTOM_MARGIN);
    lineEndPos.setX(this->width());
    lineEndPos.setY(height - BOTTOM_MARGIN);
    painter.setPen(QColor(206, 206, 206));
    painter.drawLine(lineStartPos, lineEndPos);

    if (this->width() != (fatherWidget->width() - borderWidth))
    {
        this->setFixedWidth(fatherWidget->width() - borderWidth);
    }

    QWidget::paintEvent(event);
}

void TitleBar::mouseDoubleClickEvent(QMouseEvent *event)
{
	//logi.2018.6.24
	if(buttonType == MIN_MAX_BUTTON)
		return;
	
    if (buttonType == MIN_MAX_BUTTON)
    {
        if (maxButton->isVisible())
        {
            onMaxButtonClicked();
        }
        else
        {
            onRestoreButtonClicked();
        }
    }

    return QWidget::mouseDoubleClickEvent(event);
}

void TitleBar::mousePressEvent(QMouseEvent *event)
{
    if (buttonType == MIN_MAX_BUTTON)
    {
        if (maxButton->isVisible())
        {
            fPressed = true;
            startPos = event->globalPos();
        }
    }
    else
    {
        fPressed = true;
        startPos = event->globalPos();
    }

    return QWidget::mousePressEvent(event);
}

void TitleBar::mouseMoveEvent(QMouseEvent *event)
{
    if (fPressed)
    {
        QPoint movePoint = event->globalPos() - startPos;
        QPoint widgetPos = fatherWidget->pos();
        startPos = event->globalPos();
        fatherWidget->move(widgetPos.x() + movePoint.x(), widgetPos.y() + movePoint.y());
    }
    return QWidget::mouseMoveEvent(event);
}

void TitleBar::mouseReleaseEvent(QMouseEvent *event)
{
    fPressed = false;
    return QWidget::mouseReleaseEvent(event);
}

void TitleBar::onMinButtonClicked()
{
    emit sigMinButtonClicked();
}

void TitleBar::onRestoreButtonClicked()
{
    restoreButton->setVisible(false);
    maxButton->setVisible(true);
    emit sigRestoreButtonClicked();
}

void TitleBar::onMaxButtonClicked()
{
    maxButton->setVisible(false);
    restoreButton->setVisible(true);
    emit sigMaxButtonClicked();
}

void TitleBar::onCloseButtonClicked()
{
    emit sigCloseButtonClicked();
}

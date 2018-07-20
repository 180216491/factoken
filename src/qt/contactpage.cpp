#include "contactpage.h"
#include "ui_contactpage.h"

ContactPage::ContactPage(QWidget *parent) :
    QWidget(parent),
    ui(new Ui::ContactPage)
{
    ui->setupUi(this);

    ui->label_0->setFixedHeight(160);

    ui->labelWebsite->setText(tr("    Website: ") + tr("http://www.facchain.cc"));
    ui->labelEmail->setText(tr("    Email: ") + tr("fashionchain@hotmail.com"));
	//关于我们的介绍，下以可以增加-logi,对应应该改.ts文件
    //ui->labelWechat->setText(tr("    Wechat: ") + tr("wechatnumber"));
    //ui->labelQQGroup->setText(tr("    QQ group: ") + tr("qqnumber"));
    //ui->labelTelegraphGroup->setText(tr("    Telegraph group: ") + tr("https://t.me/factoken"));
}

ContactPage::~ContactPage()
{
    delete ui;
}

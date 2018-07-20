#include "votepage.h"
#include "ui_votepage.h"

VotePage::VotePage(QWidget *parent) :
    QWidget(parent),
    ui(new Ui::VotePage)
{
    ui->setupUi(this);
}

VotePage::~VotePage()
{
    delete ui;
}

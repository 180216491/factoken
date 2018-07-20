#ifndef CONTACTPAGE_H
#define CONTACTPAGE_H

#include <QWidget>

namespace Ui {
class ContactPage;
}

class ContactPage : public QWidget
{
    Q_OBJECT

public:
    explicit ContactPage(QWidget *parent = 0);
    ~ContactPage();

private:
    Ui::ContactPage *ui;
};

#endif // CONTACTPAGE_H

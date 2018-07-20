#ifndef NTP_CLIENT_H
#define NTP_CLIENT_H

#include <string>

//Components of the Boost Library
#include <boost/array.hpp>
#include <boost/asio.hpp>

/**
 *  A Network Time Protocol Client that queries the DateTime from the Time Server located at hostname
 */
class NtpClient
{
private:
    std::string _host_name;
    unsigned short _port;

public:
    NtpClient(std::string i_hostname);
    time_t RequestDatetime();
};


#endif

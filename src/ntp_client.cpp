#include "ntp_client.h"
#include "util.h"
#include <iostream>
#include <boost/bind.hpp>
#include <boost/date_time/posix_time/posix_time_types.hpp>
#include <boost/asio/ip/udp.hpp>
#include <boost/asio/io_service.hpp>
#include <boost/asio/deadline_timer.hpp>

/**
 *  NtpClient
 *  @Param i_hostname - The time server host name which you are connecting to obtain the time
 *                      eg. the pool.ntp.org project virtual cluster of timeservers
 */
NtpClient::NtpClient(std::string i_hostname) : _host_name(i_hostname), _port(123)
{
}

void CheckTimer(boost::asio::deadline_timer* pTimer, boost::asio::ip::udp::socket* pSocket)
{
    if(!pTimer || !pSocket)
        return;

    if(pTimer->expires_at() <= boost::asio::deadline_timer::traits_type::now())
    {
        pSocket->cancel();
        pTimer->expires_at(boost::posix_time::pos_infin);
    }

    pTimer->async_wait(boost::bind(&CheckTimer, pTimer, pSocket));
}

void SendHandler(const boost::system::error_code& err, size_t len)
{
}

void RecvHandler(const boost::system::error_code& err, size_t len, boost::system::error_code* pErr, size_t* pLen)
{
    *pErr = err;
    *pLen = len;
}

/**
 * RequestDatetime_UNIX()
 * @Returns time_t - number of seconds from the Unix Epoch start time
 */
time_t NtpClient::RequestDatetime()
{
    time_t timeRecv = 0;
    try{
        boost::asio::io_service _service;
        boost::asio::ip::udp::resolver _resolver(_service);
        boost::asio::ip::udp::resolver::query _query(boost::asio::ip::udp::v4(), this->_host_name, "ntp");
        boost::asio::ip::udp::endpoint _receiver_endpoint = *_resolver.resolve(_query);
        boost::asio::ip::udp::socket _socket(_service);
        _socket.open(boost::asio::ip::udp::v4());

        boost::array<unsigned char, 48> _sendBuf  = {010, 0, 0, 0, 0, 0, 0, 0, 0};
        _socket.async_send_to(boost::asio::buffer(_sendBuf), _receiver_endpoint, SendHandler);

        boost::array<unsigned long, 1024> _recvBuf;
        boost::asio::ip::udp::endpoint _sender_endpoint;

        boost::asio::deadline_timer _timer(_service);
        _timer.expires_at(boost::posix_time::pos_infin);
        CheckTimer(&_timer, &_socket);

        boost::posix_time::time_duration timeout = boost::posix_time::seconds(10);
        _timer.expires_from_now(timeout);

        size_t len = 0;
        boost::system::error_code err = boost::asio::error::would_block;
        _socket.async_receive_from(boost::asio::buffer(_recvBuf), _sender_endpoint, boost::bind(&RecvHandler, _1, _2, &err, &len));

        do{
            _service.run_one();
        }while(err == boost::asio::error::would_block);

#ifndef WIN32
        timeRecv = ntohl((time_t)_recvBuf[4]);
#else
        timeRecv = ntohl((time_t)_recvBuf[8]);
#endif
        timeRecv -= 2208988800U;

        _socket.close();
    }catch (std::exception& e){
        std::cerr << e.what() << std::endl;
    }

    return timeRecv;
}

/*
time_t NtpClient::RequestDatetime()
{
    boost::asio::io_service io_service;
    boost::asio::ip::udp::resolver resolver(io_service);
    boost::asio::ip::udp::resolver::query query(boost::asio::ip::udp::v4(), this->_host_name, "ntp");
    boost::asio::ip::udp::endpoint receiver_endpoint = *resolver.resolve(query);
    boost::asio::ip::udp::socket socket(io_service);
    socket.open(boost::asio::ip::udp::v4());

    boost::array<unsigned char, 48> sendBuf = { 010, 0, 0, 0, 0, 0, 0, 0, 0 };
    socket.send_to(boost::asio::buffer(sendBuf), receiver_endpoint);

    boost::array<unsigned long, 1024> recvBuf = { 0 };
    boost::asio::ip::udp::endpoint sender_endpoint;

    time_t timeRecv = 0;
    try{
        size_t len = socket.receive_from(boost::asio::buffer(recvBuf), sender_endpoint);
        timeRecv = ntohl((time_t)recvBuf[4]);
        timeRecv -= 2208988800U;  //Unix time starts from 01/01/1970 == 2208988800U
    }catch (std::exception& e){
        std::cerr << e.what() << std::endl;
    }

#ifndef WIN32
    timeRecv = ntohl((time_t)_recvBuf[4]);
#else
    timeRecv = ntohl((time_t)_recvBuf[8]);
#endif
    timeRecv -= 2208988800U;

    socket.close();

    return timeRecv;
}
*/


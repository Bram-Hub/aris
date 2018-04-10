# Aris

Aris is a logical proof program, and supports propositional and predicate logic, Boolean algebra, and arithmetical logic

## Installing

For production installation it is recommended to use the latest packages available in the [Releases](https://github.com/cicchr/ARIS-Java/releases) section of the repository 

### Client

TODO

### Server

1. Download and install the server package for your system from the [Releases](https://github.com/cicchr/ARIS-Java/releases) section
2. Obtain signed SSL certificates from a valid certificate authority and save the ca certificate and private key using the x509 encoded pem format
3. Modify the file located in /etc/aris.cfg and set the ca and key options to the location where the certificate and key are stored
    ```
    #/etc/aris.cfg
    
    # The CA certificate to use for connections. If this is not specified here or on the command line the server will run in self signing mode
    ca <certificate-file-path>
    
    # The private key for the above certificate. If this is not specified here of on the command line the server will run in self signing mode
    key <key-file-path>
    ```
4. Run the aris-configdb command and follow the prompts to configure the database and start the server
    ```
    aris-configdb
    ```
5. Run the aris-adduser command and follow the prompts to create an instructor on the server for access through the standard interface
    ```
    aris-adduser
    ``` 
6. Ensure the port (default: 9001) is open on the server's firewall
 
Note: To run the server in self signing mode skip steps 2 and 3 (not recommended)

## Authors

* **Ryan Cicchiello** - *Developer* - [Github](https://github.com/cicchr)
* **Logan Garber** - *Developer* - [Github](https://github.com/garberlog)
* **Ian Dunn** - *Initial work*

## License

This project is licensed under the GNU GPLv3 License - see the [LICENSE](LICENSE) file for details

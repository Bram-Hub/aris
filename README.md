# Aris

Aris is a logical proof program, and supports propositional and predicate logic, Boolean algebra, and arithmetical logic

## Installing

For production installation it is recommended to use the latest packages available in the [Releases](https://github.com/cicchr/ARIS-Java/releases) section of the repository 

### Client

* Windows
    1. Download the installer from the [Releases](https://github.com/cicchr/ARIS-Java/releases) section
    2. Run the installer (Note: Windows Smart Screen filter may show a warning due to the installer not being signed. To bypass this click "More Info" then "Run Anyway")
    3. Follow the installer's prompts
    4. Aris should now be installed and accessible from the Start Menu 

* Debian/Ubuntu
    1. Install gradle, the OpenJDK 8, and openjfx (e.g. via `sudo apt-get install gradle openjdk-8-jdk openjfx openjfx`)
    2. Compile and run with `gradle proof-client:run`

### Server

1. Download and install the server package for your system from the [Releases](https://github.com/cicchr/ARIS-Java/releases) section
2. Obtain signed SSL certificates from a valid certificate authority and save the ca certificate and private key using the x509 encoded pem format
3. Create a configuration file in /etc/aris.d/ (You can add a 2 digit prefix to set the file's priority) and set the ca and key options to the location where the certificate and key are stored
    ```
    # /etc/aris.d/<filename>
    
    # The CA certificate to use for connections. If this is not specified here or on the command line the server will run in self signing mode
    ca <certificate-file-path>
    
    # The private key for the above certificate. If this is not specified here of on the command line the server will run in self signing mode
    key <key-file-path>
    ```
4. Setup the database.
    
    Note: The aris-configdb command exists to simplify this process. Otherwise follow the steps listed below.
    
    1. Either create a new configuration file in /etc/aris.d/ or use the same as in step 3 and add the following settings
        ```
        # /etc/aris.d/<filename>
        
        # The name of the postgres database for Aris to use
        db-name <aris-database-name>
        
        # The postgres database user for Aris to login as
        db-user <aris-username>
        
        # The postgres database password
        db-pass <aris-password>
        
        # The domain name of the server
        # Note this is only required when the server is running in self signing mode
        domain <example.com>
        ```
    2. Create the database for aris in postgres. You can either run the commands listed below or run the aris-createdb script as root which will run the below commands using the settings from the previous step
        ```
        # Open the postgres interactive terminal
        sudo -u postgres psql
        
        # The following commands should be run in postgres
        CREATE DATABASE <aris-database-name>;
        CREATE USER <aris-username>;
        ALTER USER <aris-username> WITH ENCRYPTED PASSWORD '<aris-password>';
        GRANT ALL PRIVILEGES ON DATABASE <aris-database-name> TO <aris-username>;
        
        # To exit the interface type \q
        ``` 
5. Start and enable the service

    ```
    sudo systemctl start aris
    sudo systemctl enable aris
    ```
    
6. Ensure the port (default: 9001) is open on the server's firewall
7. Once the server has started you can use the client to login using the default login:
    ```
    User: admin
    Password: ArisAdmin1
    ```
 
Note: To run the server in self signing mode skip steps 2 and 3 (not recommended)

## Modules

This repository contains several modules for the Aris platform which are as follows:

* **assign-client** - Contains the Aris Assign client side code including an extensible framework to add new client side modules
* **assign-server** - Contains the Aris Assign server side code including an extensible framework to add new server side modules
* **libaris** - Contains a logic software library used by Aris Proof
* **libassign** - Contains common code used by both assign-server and assign-client
* **packaging** - Contains files for building distribution packages
* **proof-client** - Contains the client side module of the Aris Proof system
* **proof-server** - Contains the server side module of the Aris Proof system
* **service** - Contains the ServiceLoader interfaces required to add new programs to the Aris platform

## Authors

* **Ryan Cicchiello** - *Developer* - [Github](https://github.com/cicchr)
* **Logan Garber** - *Developer* - [Github](https://github.com/garberlog)
* **Ian Dunn** - *Initial work*

## License

This project is licensed under the GNU GPLv3 License - see the [LICENSE](LICENSE) file for details

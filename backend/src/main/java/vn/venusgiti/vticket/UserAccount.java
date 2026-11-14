package vn.venusgiti.vticket;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="user_accounts")
public class UserAccount {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true,length=100) public String username;
 @Column(nullable=false,length=150) public String displayName;
 @Column(nullable=false,length=100) public String passwordHash;
 @Column(nullable=false,length=16) public String role;
 @Column(nullable=false) public boolean enabled=true;
 @Column(nullable=false) public long securityVersion;
 @Column(nullable=false) public Instant createdAt;
}

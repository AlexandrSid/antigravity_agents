-- V2: Seed 20 Addresses and 1000 interconnected Users across 3 generations

INSERT INTO addresses (id, country, city, street, building, apartment, postal_code) VALUES
(1, 'Germany', 'Berlin', 'Alexanderplatz', '1', '101', '10178'),
(2, 'Germany', 'Munich', 'Marienplatz', '2', '202', '80331'),
(3, 'Germany', 'Hamburg', 'Jungfernstieg', '3', '303', '20354'),
(4, 'Germany', 'Frankfurt', 'Zeil', '4', '404', '60313'),
(5, 'Germany', 'Cologne', 'Schildergasse', '5', '505', '50667'),
(6, 'France', 'Paris', 'Rue de Rivoli', '10', '1A', '75001'),
(7, 'France', 'Lyon', 'Rue de la Republique', '20', '2B', '69002'),
(8, 'France', 'Marseille', 'La Canebiere', '30', '3C', '13001'),
(9, 'UK', 'London', 'Oxford Street', '100', 'Flat 1', 'W1D 1LL'),
(10, 'UK', 'Manchester', 'Deansgate', '200', 'Flat 2', 'M3 2BW'),
(11, 'USA', 'New York', 'Broadway', '500', 'Apt 4A', '10012'),
(12, 'USA', 'San Francisco', 'Market Street', '600', 'Suite 10', '94103'),
(13, 'USA', 'Chicago', 'Michigan Avenue', '700', 'Apt 12B', '60611'),
(14, 'Canada', 'Toronto', 'Yonge Street', '800', 'Unit 5', 'M4Y 1W9'),
(15, 'Canada', 'Vancouver', 'Robson Street', '900', 'Unit 8', 'V6Z 1A1'),
(16, 'Netherlands', 'Amsterdam', 'Damrak', '15', 'Kamer 3', '1012 LG'),
(17, 'Austria', 'Vienna', 'Kaerntner Strasse', '25', 'Top 4', '1010'),
(18, 'Switzerland', 'Zurich', 'Bahnhofstrasse', '35', 'Stock 2', '8001'),
(19, 'Poland', 'Warsaw', 'Nowy Swiat', '45', 'Mieszk 6', '00-029'),
(20, 'Spain', 'Madrid', 'Gran Via', '55', 'Piso 3', '28013');

-- Generation 1 (Grandparents, IDs 1..200, Born 1940..1960, No parents)
-- Generation 2 (Parents, IDs 201..600, Born 1965..1985, Parents in Gen 1)
-- Generation 3 (Children, IDs 601..1000, Born 1990..2010, Parents in Gen 2)

-- Helper stored procedure / H2 script to populate 1000 users deterministically with parental invariants
-- H2 allows Java/SQL looping or statements
CREATE ALIAS IF NOT EXISTS SEED_USERS AS $$
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;

@_
public class UserSeeder {
    public static void seed(Connection conn) throws SQLException {
        String[] firstNames = {"James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda", "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Sarah", "Charles", "Karen"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin"};

        String sql = "INSERT INTO users (id, first_name, last_name, birth_date, email, phone_number, address_id, father_id, mother_id, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int uid = 1; uid <= 1000; uid++) {
                String fn = firstNames[uid % firstNames.length];
                String ln = lastNames[uid % lastNames.length];
                String email = "user" + uid + "@example.com";
                String phone = String.format("+1200%06d", uid);
                
                long addrId;
                LocalDate birthDate;
                Long fatherId = null;
                Long motherId = null;

                if (uid <= 200) {
                    addrId = (uid % 20) + 1;
                    int year = 1940 + (uid % 21);
                    birthDate = LocalDate.of(year, (uid % 12) + 1, (uid % 28) + 1);
                } else if (uid <= 600) {
                    int pOffset = (uid - 201) % 100;
                    fatherId = (long)(1 + pOffset * 2);
                    motherId = (long)(2 + pOffset * 2);
                    addrId = (fatherId % 20) + 1;
                    int year = 1965 + ((uid - 200) % 21);
                    birthDate = LocalDate.of(year, (uid % 12) + 1, (uid % 28) + 1);
                } else {
                    int pOffset = (uid - 601) % 200;
                    fatherId = (long)(201 + pOffset * 2);
                    motherId = (long)(202 + pOffset * 2);
                    addrId = ((fatherId - 200) % 20) + 1;
                    int year = 1990 + ((uid - 600) % 21);
                    birthDate = LocalDate.of(year, (uid % 12) + 1, (uid % 28) + 1);
                }

                ps.setLong(1, uid);
                ps.setString(2, fn);
                ps.setString(3, ln);
                ps.setDate(4, Date.valueOf(birthDate));
                ps.setString(5, email);
                ps.setString(6, phone);
                ps.setLong(7, addrId);
                if (fatherId != null) ps.setLong(8, fatherId); else ps.setNull(8, java.sql.Types.BIGINT);
                if (motherId != null) ps.setLong(9, motherId); else ps.setNull(9, java.sql.Types.BIGINT);
                
                ps.addBatch();
                if (uid % 100 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }
}
$$;

CALL SEED_USERS();
DROP ALIAS SEED_USERS;

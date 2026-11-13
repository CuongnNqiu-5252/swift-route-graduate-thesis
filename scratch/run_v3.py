import psycopg2

conn = psycopg2.connect(host='localhost', port=5432, user='openpg', password='openpgpwd', dbname='driver_db')
cur = conn.cursor()

with open('services/driver-service/src/main/resources/db/migration/V3__create_vehicles.sql', 'r', encoding='utf-8') as f:
    sql = f.read()

cur.execute(sql)
conn.commit()
print("Successfully executed V3__create_vehicles.sql on driver_db")

cur.execute("SELECT table_name FROM information_schema.tables WHERE table_name='vehicles';")
print("Vehicles table:", cur.fetchall())
conn.close()

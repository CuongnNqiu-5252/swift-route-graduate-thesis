import psycopg2

def inspect_db(dbname):
    print(f"=== DB: {dbname} ===")
    conn = psycopg2.connect(host='localhost', port=5432, user='openpg', password='openpgpwd', dbname=dbname)
    cur = conn.cursor()
    cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public';")
    tables = [r[0] for r in cur.fetchall()]
    print("Tables:", tables)
    for t in tables:
        if 'flyway' in t:
            cur.execute(f"SELECT installed_rank, version, description, type, script, success FROM {t} ORDER BY installed_rank;")
            rows = cur.fetchall()
            print(f"  {t} history:")
            for row in rows:
                print("   ", row)
    conn.close()

for db in ['auth_db', 'order_db', 'driver_db', 'notification_db']:
    try:
        inspect_db(db)
    except Exception as e:
        print(f"Error inspecting {db}: {e}")

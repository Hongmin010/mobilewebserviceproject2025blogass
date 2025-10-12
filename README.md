
Run:
python -m venv .venv
# Windows: .venv\Scripts\Activate.ps1
- pip install -r requirements.txt
- python manage.py makemigrations blog
- python manage.py migrate
- python manage.py createsuperuser
- python manage.py runserver

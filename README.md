
# imgblog (PPT-aligned)
View/DB, Template/CSS, Forms 흐름 반영 + 이미지 업로드 + REST API 유지.

Run:
python -m venv .venv
# Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py makemigrations blog
python manage.py migrate
python manage.py createsuperuser
python manage.py runserver

FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8080

WORKDIR /srv
COPY requirements.txt /srv/requirements.txt
RUN pip install --no-cache-dir -r /srv/requirements.txt

# The build context is waitlist/, so the package lands complete: the app is
# imported as waitlist.app.main with /srv on the path.
COPY . /srv/waitlist

RUN useradd --create-home appuser
USER appuser
EXPOSE 8080

CMD ["uvicorn", "waitlist.app.main:app", "--app-dir", "/srv", "--host", "0.0.0.0", "--port", "8080"]

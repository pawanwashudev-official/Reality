def generate_math_problem():
    import random

    # 3 digit times 2 digit for instance
    a = random.randint(100, 999)
    b = random.randint(10, 99)


    # Check what kind of math problem to generate
    # Multiplication with decimal up to 3 digits

    # Example: 1.2 * 3.4 = ?
    # 1.23 * 4.5 = ?
    # Let's do something like a = random.randint(10, 999) / 10.0 or / 100.0 etc

    a_val = random.randint(11, 99)
    a_decimal_places = random.randint(1, 2)
    a = a_val / (10 ** a_decimal_places)

    b_val = random.randint(11, 99)
    b_decimal_places = random.randint(1, 2)
    b = b_val / (10 ** b_decimal_places)

    total_decimals = a_decimal_places + b_decimal_places
    if total_decimals > 3:
        if a_decimal_places == 2:
            a_decimal_places = 1
            a = a_val / 10.0
        elif b_decimal_places == 2:
            b_decimal_places = 1
            b = b_val / 10.0

    print(f"a: {a}, b: {b}, answer: {round(a * b, 3)}")

generate_math_problem()
generate_math_problem()
generate_math_problem()

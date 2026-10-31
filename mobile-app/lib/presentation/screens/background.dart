import 'package:flutter/material.dart';

class BuildBackground extends StatelessWidget {
  const BuildBackground({super.key, this.isLogin = false});
  final bool isLogin;
  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      height: double.infinity,
      decoration: const BoxDecoration(
        image: DecorationImage(
          image: AssetImage("../assets/images/backgroundIMG.jpg"),
          fit: BoxFit.cover,
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: EdgeInsets.only(top: 10),
          child: Align(
            alignment: Alignment.topCenter,
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 30.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      IconButton(
                        onPressed: () {
                          Navigator.pop(context);
                        },
                        icon: const Icon(Icons.arrow_back, color: Colors.white),
                      ),
                      isLogin
                          ? Row(
                              children: [
                                const Text(
                                  "Don't have an account?",
                                  style: TextStyle(color: Colors.white70),
                                ),
                                const SizedBox(width: 8),
                                TextButton(
                                  onPressed: () {
                                    Navigator.pushNamed(context, '/register');
                                  },
                                  style: TextButton.styleFrom(
                                    backgroundColor: Colors.white24,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 6,
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    textStyle: const TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  child: const Text(
                                    "Get Started",
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ),
                              ],
                            )
                          : Row(
                              children: [
                                const Text(
                                  "Already have an account?",
                                  style: TextStyle(color: Colors.white70),
                                ),
                                const SizedBox(width: 8),
                                TextButton(
                                  onPressed: () {
                                    Navigator.pushNamed(context, '/login');
                                  },
                                  style: TextButton.styleFrom(
                                    backgroundColor: Colors.white24,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 6,
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    textStyle: const TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  child: const Text(
                                    "Login",
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                    ],
                  ),
                ),
                Text(
                  "Jobsly",
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
